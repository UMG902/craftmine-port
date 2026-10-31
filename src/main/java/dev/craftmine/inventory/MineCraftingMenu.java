package dev.craftmine.inventory;

import dev.craftmine.CraftmineConfig;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.duck.CraftmineSlotDuck;
import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineRegistries;
import dev.craftmine.server.CraftmineServerHooks;
import dev.craftmine.unlock.PlayerUnlocks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import dev.craftmine.server.DimensionGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import dev.craftmine.item.component.WorldModifiers;
import net.minecraft.world.level.block.Blocks;
import dev.craftmine.block.entity.MineCrafterBlockEntity;
import net.minecraft.world.level.dimension.LevelStem;
import dev.craftmine.mines.SpecialMine;
import dev.craftmine.mines.WorldEffect;
import dev.craftmine.mines.WorldEffectSet;
import dev.craftmine.mines.WorldEffects;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec2;

/**
 * The minecrafter menu (the block the whole mod is named after): ingredient
 * slots arranged on a spiral, the mine result slot, the unlocked-effect
 * drawer rows and the locked-effect hint grid. Ingredient changes rebuild
 * the crafted-mine stack ({@link #slotsChanged}); picking the result opens
 * the mine ({@link #onOpenMine} -> async dimension generation). Slot counts
 * scale with the crafter block's level and arrive as the menu's additional
 * sync data.
 */
public class MineCraftingMenu
extends AbstractContainerMenu implements dev.craftmine.duck.CraftmineMenuDuck {
    public static final List<Vec2> SPIRAL_POSITIONS = List.of(new Vec2(93.0f, 82.0f), new Vec2(117.0f, 69.0f), new Vec2(115.0f, 41.0f), new Vec2(85.0f, 31.0f), new Vec2(57.0f, 54.0f), new Vec2(63.0f, 89.0f), new Vec2(128.0f, 96.0f), new Vec2(150.0f, 68.0f), new Vec2(148.0f, 30.0f), new Vec2(111.0f, 15.0f), new Vec2(60.0f, 15.0f), new Vec2(31.0f, 32.0f), new Vec2(22.0f, 57.0f), new Vec2(27.0f, 89.0f));
    private final GhettoSpline ghettoSpline = new GhettoSpline(SPIRAL_POSITIONS);
    private final ContainerLevelAccess access;
    private final Consumer<ResourceKey<LevelStem>> dimensionCreatedCallback;
    private final List<MineCraftingSlot> craftingSlots = new ArrayList<MineCraftingSlot>();
    private final MineCraftingResultSlot resultSlot;
    private final List<MineCraftingDrawerSlot> drawerSlots = new ArrayList<MineCraftingDrawerSlot>();
    private final List<MineCraftingHintSlot> hintSlots = new ArrayList<MineCraftingHintSlot>();
    public static final int DRAWER_NUM_COLS = 9;
    public static final int DRAWER_NUM_ROWS = 4;
    private static final int HINTS_NUM_COLS = 6;
    private static final int HINTS_NUM_ROWS = 10;
    private int nrOfSlots;
    private int nrOfRandomSlots;
    private int currentLevel;
    private int currentExp;
    private final Optional<ServerLevel> serverLevel;
    private final Optional<ServerPlayer> serverPlayer;
    private final Optional<BlockPos> pos;
    /** Unoriginal reroll: while true populateRandomEffects must never swap in a special mine. */
    private boolean rerolling;

    public MineCraftingMenu(int n, Inventory inventory, List<Integer> list) {
        this(n, inventory, ContainerLevelAccess.NULL, new SimpleContainer(99), resourceKey -> {}, list);
    }

    public MineCraftingMenu(int n, Inventory inventory, ContainerLevelAccess containerLevelAccess, Container container, Consumer<ResourceKey<LevelStem>> consumer, List<Integer> list) {
        super(CraftmineContent.MAP_MAKING, n);
        this.setAdditionalData(list);
        MineCraftingMenu.checkContainerSize(container, 99);
        this.access = containerLevelAccess;
        this.dimensionCreatedCallback = consumer;
        container.startOpen(inventory.player);
        Player player = inventory.player;
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            this.serverPlayer = Optional.of(serverPlayer);
        } else {
            this.serverPlayer = Optional.empty();
        }
        this.pos = containerLevelAccess.evaluate((level, blockPos) -> blockPos);
        this.serverLevel = containerLevelAccess.evaluate((level, blockPos) -> {
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)level;
                return serverLevel;
            }
            return null;
        });
        this.resultSlot = (MineCraftingResultSlot)this.addSlot(new MineCraftingResultSlot(container, 0, 88, 58, this));
        this.addCraftingSlots(container);
        this.updateCraftingSlots();
        this.populateRandomEffects();
        this.addMineEffectDrawer();
        this.addLockedEffectHintBox();
        this.slotsChanged(container);
    }

    private void setAdditionalData(List<Integer> list) {
        this.currentLevel = list.get(0);
        this.currentExp = list.get(1);
        this.nrOfSlots = MineCrafterBlockEntity.craftingSlotsForLevel(this.currentLevel);
        this.nrOfRandomSlots = MineCrafterBlockEntity.randomCraftingSlotsForLevel(this.currentLevel);
    }

    public int getCurrentLevel() {
        return this.currentLevel;
    }

    public int getCurrentExp() {
        return this.currentExp;
    }

    private void addCraftingSlots(Container container) {
        for (int i = 0; i < 50; ++i) {
            MineCraftingSlot mineCraftingSlot = (MineCraftingSlot)this.addSlot(new MineCraftingSlot(container, 1 + i, 0, 0, this, true));
            mineCraftingSlot.setActive(false);
            this.craftingSlots.add(mineCraftingSlot);
        }
    }

    private void updateCraftingSlots() {
        float f = (float)(SPIRAL_POSITIONS.size() - 1) / (float)this.nrOfSlots;
        int n = 0;
        for (MineCraftingSlot mineCraftingSlot : this.craftingSlots) {
            boolean bl;
            if (n >= this.nrOfSlots) {
                mineCraftingSlot.randomSlot = false;
                mineCraftingSlot.setActive(false);
                continue;
            }
            float f2 = f * (float)n;
            Vec2 vec2 = SPIRAL_POSITIONS.get((int)Math.floor(f2));
            Vec2 vec22 = SPIRAL_POSITIONS.get((int)Math.floor(f2) + 1);
            Vec2 vec23 = vec2.add(vec22.add(vec2.negated()).scale(f2 - (float)Mth.floor(f2)));
            boolean bl2 = bl = n < this.nrOfRandomSlots;
            if (!mineCraftingSlot.randomSlot && bl) {
                mineCraftingSlot.set(ItemStack.EMPTY);
            }
            mineCraftingSlot.randomSlot = bl;
            ((CraftmineSlotDuck)mineCraftingSlot).craftmine$setPosition((int)vec23.x, (int)vec23.y);
            mineCraftingSlot.setActive(true);
            ++n;
        }
    }

    private void populateRandomEffects() {
        if (!this.serverLevel.isPresent()) {
            return;
        }
        if (this.isBossMine()) {
            return;
        }
        boolean bl = this.getCraftingEffects().findAny().isEmpty();
        Optional<SpecialMine> optionalSpecialMine = !this.rerolling && bl && this.resultSlot.getItem().isEmpty() ? ((CraftmineLevelDuck)this.serverLevel.get()).getNextSpecialMine() : Optional.empty();
        if (optionalSpecialMine.isPresent()) {
            this.resultSlot.set(MineCraftingMenu.createSpecialMineItem(optionalSpecialMine.get()));
            this.broadcastChanges();
            return;
        }
        List<WorldEffect> list3 = this.getMustHaveEffects();
        ArrayList<WorldEffect> arrayList = new ArrayList<WorldEffect>();
        for (MineCraftingSlot mineCraftingSlot : this.craftingSlots) {
            if (!mineCraftingSlot.randomSlot || mineCraftingSlot.hasItem()) continue;
            if (!list3.isEmpty()) {
                int n = this.getNextLevel();
                mineCraftingSlot.set(WorldEffects.createEffectItem(Component.translatableEscape("world.mine.base", n), false, List.copyOf(list3)));
                arrayList.addAll(list3);
                list3 = List.of();
                continue;
            }
            Optional<WorldEffect> optional = MineCraftingMenu.getRandomEffect(this.serverLevel.get(), arrayList, this.getCraftingEffects().collect(Collectors.toSet()));
            optional.ifPresent(worldEffect -> {
                mineCraftingSlot.set(WorldEffects.createEffectItem(worldEffect, false));
                arrayList.add(worldEffect);
            });
        }
    }

    private static ItemStack createSpecialMineItem(SpecialMine specialMine) {
        ItemStack itemStack = new ItemStack(CraftmineContent.MINE);
        itemStack.set(CraftmineDataComponents.WORLD_MODIFIERS, new WorldModifiers(new ArrayList<WorldEffect>(), false));
        itemStack.set(DataComponents.ITEM_NAME, specialMine.name());
        itemStack.set(DataComponents.LORE, new ItemLore(List.of(specialMine.description())));
        itemStack.set(CraftmineDataComponents.SPECIAL_MINE, specialMine);
        return itemStack;
    }

    /**
     * Unoriginal feature (config unoriginal_features): the minecrafter reroll button.
     * <ul>
     *   <li>Normal mine: every random ingredient slot (the "Mine N Base" ingredient and whatever
     *   else was rolled by default) is cleared and rolled again. Ingredients the player placed
     *   themselves are kept, and a reroll never turns a normal mine into a special one.</li>
     *   <li>Special mine: picks a different special mine from the ones available (needs the
     *   "reroll special mines" unlock as well).</li>
     * </ul>
     * Server-authoritative: re-checks config, unlocks and mine state; the client button is only a shortcut.
     */
    public void rerollIngredients(ServerPlayer serverPlayer) {
        if (this.serverLevel.isEmpty() || !CraftmineConfig.rerollTree()) {
            return;
        }
        if (this.isMineActive() || this.isMineCompleted()) {
            return;
        }
        dev.craftmine.duck.CraftminePlayerDuck playerDuck = ((CraftminePlayerDuck)serverPlayer);
        if (!playerDuck.isActive(PlayerUnlocks.QOL_REROLL)) {
            return;
        }
        if (this.isBossMine()) {
            if (!playerDuck.isActive(PlayerUnlocks.QOL_REROLL_SPECIAL)) {
                return;
            }
            SpecialMine current = this.resultSlot.getItem().get(CraftmineDataComponents.SPECIAL_MINE);
            dev.craftmine.duck.CraftmineLevelDuck levelDuck = (dev.craftmine.duck.CraftmineLevelDuck)(Object)this.serverLevel.get();
            for (int i = 0; i < 16; ++i) {
                Optional<SpecialMine> candidate = levelDuck.getNextSpecialMine();
                if (candidate.isPresent() && !candidate.get().equals(current)) {
                    this.resultSlot.set(MineCraftingMenu.createSpecialMineItem(candidate.get()));
                    break;
                }
            }
        } else {
            this.rerolling = true;
            try {
                for (MineCraftingSlot mineCraftingSlot : this.craftingSlots) {
                    if (mineCraftingSlot.randomSlot && mineCraftingSlot.hasItem()) {
                        mineCraftingSlot.set(ItemStack.EMPTY);
                    }
                }
                this.populateRandomEffects();
                this.slotsChanged(this.resultSlot.container);
            } finally {
                this.rerolling = false;
            }
        }
        this.broadcastChanges();
    }

    public boolean isBossMine() {
        return this.resultSlot.hasItem() && this.resultSlot.getItem().has(CraftmineDataComponents.SPECIAL_MINE);
    }

    public static Optional<WorldEffect> getRandomEffect(ServerLevel serverLevel, List<WorldEffect> list, Set<WorldEffect> set) {
        WeightedList.Builder<WorldEffect> builder = WeightedList.builder();
        for (WorldEffect worldEffect : CraftmineRegistries.WORLD_EFFECT) {
            if (worldEffect.inSets().stream().anyMatch(WorldEffectSet::exclusive) || worldEffect.randomWeight() <= 0 || !worldEffect.isValidWith(list) || set.contains(worldEffect) || !worldEffect.canRandomize(serverLevel)) continue;
            boolean bl = ((CraftmineLevelDuck)serverLevel).isEffectUnlocked(worldEffect);
            if (bl) {
                builder.add(worldEffect, worldEffect.randomWeight());
                continue;
            }
            builder.add(worldEffect, (int)((float)worldEffect.randomWeight() * 0.1f));
        }
        return builder.build().getRandom(serverLevel.getRandom());
    }

    private List<WorldEffect> getMustHaveEffects() {
        ServerLevel serverLevel = this.serverLevel.get();
        RandomSource randomSource = serverLevel.getRandom();
        ArrayList<WorldEffect> arrayList = new ArrayList<WorldEffect>();
        for (WorldEffectSet worldEffectSet : CraftmineRegistries.WORLD_EFFECT_SET) {
            arrayList.add(this.pickRandomEffect(worldEffectSet.effects().stream().filter(worldEffect -> worldEffect.canRandomize(serverLevel)).toList(), arrayList, randomSource));
        }
        Collections.shuffle(arrayList);
        return arrayList;
    }

    private WorldEffect pickRandomEffect(List<WorldEffect> list, List<WorldEffect> list2, RandomSource randomSource) {
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Tried to pick random effect from fully incompatible sets");
        }
        WorldEffect worldEffect = Util.getRandom(list, randomSource);
        if (worldEffect.isValidWith(list2)) {
            return worldEffect;
        }
        return this.pickRandomEffect(list.stream().filter(worldEffect2 -> worldEffect2 != worldEffect).toList(), list2, randomSource);
    }

    private int getNextLevel() {
        if (this.serverLevel.isEmpty()) {
            return 0;
        }
        LevelData levelData = CraftmineServerHooks.theGame().overworld().getLevelData();
        if (levelData instanceof dev.craftmine.duck.CraftmineLevelDataDuck duck) {
            return duck.getLevelCount() + 1;
        }
        return 1;
    }

    private void addMineEffectDrawer() {
        int n = Mth.ceil((float)CraftmineRegistries.WORLD_EFFECT.size() / 9.0f);
        SimpleContainer simpleContainer = new SimpleContainer(n * 9);
        if (this.serverLevel.isPresent()) {
            for (WorldEffect worldEffect : CraftmineRegistries.WORLD_EFFECT) {
                ItemStack itemStack = WorldEffects.createEffectItem(worldEffect, false);
                if (!worldEffect.canUse(this.serverLevel.get()) || !((CraftmineLevelDuck)this.serverLevel.get()).isEffectUnlocked(worldEffect)) continue;
                simpleContainer.addItem(itemStack);
            }
        }
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.drawerSlots.add((MineCraftingDrawerSlot)this.addSlot(new MineCraftingDrawerSlot(simpleContainer, i * 9 + j, 8 + j * 18, 124 + i * 18, this)));
            }
        }
    }

    private void addLockedEffectHintBox() {
        int n;
        SimpleContainer simpleContainer = new SimpleContainer(60);
        if (this.serverLevel.isPresent()) {
            // Task 29 (BugMine allowSoulLinkOnLanServers, always-on): the hint box
            // hides multiplayer-only effects behind the same gate as
            // canUse/canRandomize - now !isPublished(), so LAN worlds see their
            // locked-effect hints too (n == 1 only in private singleplayer).
            n = !CraftmineServerHooks.theGame().server().isPublished() ? 1 : 0;
            ArrayList<WorldEffect> arrayList = new ArrayList<WorldEffect>(CraftmineRegistries.WORLD_EFFECT.stream().toList());
            Collections.shuffle(arrayList);
            for (WorldEffect worldEffect : arrayList) {
                ItemStack itemStack;
                if (worldEffect.multiplayerOnly() && n != 0 || !simpleContainer.canAddItem(itemStack = WorldEffects.createEffectItem(worldEffect, false)) || ((CraftmineLevelDuck)this.serverLevel.get()).isEffectUnlocked(worldEffect) || !worldEffect.canUnlock(this.serverLevel.get())) continue;
                itemStack.set(CraftmineDataComponents.WORLD_EFFECT_HINT, Unit.INSTANCE);
                simpleContainer.addItem(itemStack);
            }
        }
        for (n = 0; n < 10; ++n) {
            for (int i = 0; i < 6; ++i) {
                this.hintSlots.add((MineCraftingHintSlot)this.addSlot(new MineCraftingHintSlot(simpleContainer, n * 6 + i, 205 + i * 18, 20 + n * 18, this)));
            }
        }
    }

    public GhettoSpline getSpline() {
        return this.ghettoSpline;
    }

    @Override
    public void updateData(List<Integer> list) {
        this.setAdditionalData(list);
        this.updateCraftingSlots();
        this.populateRandomEffects();
    }

    /**
     * Server-side sync payload: [currentLevel, currentExp] - exactly what
     * MineCrafterBlockEntity.getAdditionalData produces at menu creation; the
     * client rebuilds slot counts from it via updateData.
     */
    @Override
    public List<Integer> craftmine$getAdditionalData() {
        return List.of(this.currentLevel, this.currentExp);
    }

    protected void onOpenMine(ServerPlayer serverPlayer, ItemStack itemStack) {
        WorldModifiers worldModifiers = itemStack.getOrDefault(CraftmineDataComponents.WORLD_MODIFIERS, WorldModifiers.EMPTY);
        if (worldModifiers.effects().isEmpty() && !this.isBossMine()) {
            return;
        }
        List<WorldEffect> list = worldModifiers.effects();
        Optional<SpecialMine> optional = Optional.ofNullable(itemStack.get(CraftmineDataComponents.SPECIAL_MINE));
        if (optional.isPresent()) {
            list = optional.get().instantiate(serverPlayer.level());
        }
        DimensionGenerator.GeneratedDimension generatedDimension = DimensionGenerator.generateDimension(CraftmineServerHooks.theGame(), list, optional);
        // Task 58: join the async datapack writes NOW. The entry (mine
        // travelling block use) rescans the pack repository to pick the new
        // pack up; a partially written pack there would fail the resource
        // reload. The /level command path already joins via synchronize().
        generatedDimension.synchronize().run();
        ResourceKey<LevelStem> resourceKey = generatedDimension.id();
        itemStack.set(CraftmineDataComponents.DIMENSION_ID, resourceKey);
        itemStack.set(CraftmineDataComponents.MINE_ACTIVE, Unit.INSTANCE);
        this.dimensionCreatedCallback.accept(resourceKey);
        serverPlayer.closeContainer();
    }

    @Override
    public boolean stillValid(Player player) {
        return MineCraftingMenu.stillValid(this.access, player, CraftmineContent.MINE_CRAFTER);
    }

    @Override
    public void slotsChanged(Container container) {
        if (this.isMineActive() || this.isMineCompleted() || this.isBossMine() || this.serverLevel.isEmpty()) {
            return;
        }
        if (this.getCraftingSlots().stream().noneMatch(mineCraftingSlot -> mineCraftingSlot.hasItem() && !mineCraftingSlot.randomSlot) && this.getDrawerSlots().stream().anyMatch(mineCraftingDrawerSlot -> mineCraftingDrawerSlot.hasItem() && mineCraftingDrawerSlot.couldBeAddedToMine())) {
            this.resultSlot.set(ItemStack.EMPTY);
            return;
        }
        List<WorldEffect> list = this.getCraftingEffects().distinct().toList();
        if (list.isEmpty()) {
            this.resultSlot.set(ItemStack.EMPTY);
            return;
        }
        ItemStack itemStack = new ItemStack(CraftmineContent.MINE);
        itemStack.set(CraftmineDataComponents.WORLD_MODIFIERS, new WorldModifiers(list, false));
        itemStack.set(DataComponents.ITEM_NAME, this.getLevelName());
        this.resultSlot.set(itemStack);
        this.broadcastChanges();
    }

    private Component getLevelName() {
        int n = this.getNextLevel();
        if (n == 0) {
            return Component.translatable("world.mine.next");
        }
        return Component.translatableEscape("world.mine", n);
    }

    protected Stream<WorldEffect> getCraftingEffects() {
        HashMap<WorldEffectSet, WorldEffect> hashMap = new HashMap<WorldEffectSet, WorldEffect>();
        List<WorldEffect> list = this.craftingSlots.stream().map(Slot::getItem).map(itemStack -> itemStack.getOrDefault(CraftmineDataComponents.WORLD_MODIFIERS, WorldModifiers.EMPTY).effects()).flatMap(Collection::stream).toList();
        for (WorldEffect worldEffect2 : list) {
            for (WorldEffectSet worldEffectSet : worldEffect2.inSets()) {
                if (!worldEffectSet.exclusive()) continue;
                hashMap.put(worldEffectSet, worldEffect2);
            }
        }
        return list.stream().filter(worldEffect -> !MineCraftingMenu.isSuperseded(worldEffect, hashMap));
    }

    private static boolean isSuperseded(WorldEffect worldEffect, Map<WorldEffectSet, WorldEffect> map) {
        for (WorldEffectSet worldEffectSet : worldEffect.inSets()) {
            if (!worldEffectSet.exclusive() || map.get(worldEffectSet) == worldEffect) continue;
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int n) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = (Slot)this.slots.get(n);
        if (slot.hasItem()) {
            ItemStack itemStack2 = slot.getItem();
            itemStack = itemStack2.copy();
            if (n == this.resultSlot.index) {
                return ItemStack.EMPTY;
            }
            if (n > 0 && n <= this.craftingSlots.getLast().index) {
                slot.set(ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }
            if (n >= this.drawerSlots.getFirst().index && n <= this.drawerSlots.getLast().index) {
                this.moveItemStackTo(itemStack2.copy(), this.nrOfRandomSlots, this.nrOfSlots + 1, false);
                return ItemStack.EMPTY;
            }
        }
        return itemStack;
    }

    @Override
    public void clicked(int n, int n2, ContainerInput containerInput, Player player) {
        // Task 35 (BugMine fixes, always-on):
        // - preventCtrlQFreeze: Ctrl+Q (THROW, button 1) on a mine ingredient
        //   froze the client inside this menu.
        // - preventIngredientThrowing / preventIngredientSwapping: throwing or
        //   hotbar-swapping ingredients out of the mine crafter can softlock
        //   the crafted mine (ingredients are not recoverable).
        // THROW and SWAP are therefore fully cancelled in this menu, exactly
        // like BugMine's defaults.
        if (containerInput == ContainerInput.THROW || containerInput == ContainerInput.SWAP) {
            return;
        }
        if (!(containerInput != ContainerInput.PICKUP && containerInput != ContainerInput.QUICK_MOVE || n2 != 0 && n2 != 1 || n != -999)) {
            this.setCarried(ItemStack.EMPTY);
        }
        super.clicked(n, n2, containerInput, player);
    }

    public MineCraftingResultSlot getResultSlot() {
        return this.resultSlot;
    }

    public List<MineCraftingSlot> getCraftingSlots() {
        return this.craftingSlots;
    }

    public List<MineCraftingDrawerSlot> getDrawerSlots() {
        return this.drawerSlots;
    }

    public boolean isMineActive() {
        return this.resultSlot.hasItem() && this.resultSlot.getItem().get(CraftmineDataComponents.MINE_ACTIVE) != null;
    }

    public boolean isMineCompleted() {
        return this.resultSlot.hasItem() && this.resultSlot.getItem().get(CraftmineDataComponents.MINE_COMPLETED) != null;
    }

    public boolean wasMineSuccess() {
        return this.resultSlot.hasItem() && this.resultSlot.getItem().getOrDefault(CraftmineDataComponents.MINE_COMPLETED, false);
    }

    public boolean allEffectsAreUnlocked() {
        return this.hintSlots.stream().noneMatch(Slot::hasItem);
    }

    @Override
    public void removed(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ItemStack itemStack = this.getCarried();
        if (!itemStack.isEmpty()) {
            this.setCarried(ItemStack.EMPTY);
        }
    }
}

