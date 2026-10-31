/*
 * Ports craftmine additions to ServerPlayer: per-player unlock state, hub inventory
 * swap, revisiting flag and hub rewards.
 */
package dev.craftmine.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.registry.CraftmineTriggers;
import dev.craftmine.server.CraftmineServerHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import dev.craftmine.network.packet.ClientboundOpenDoorPacket;
import net.minecraft.resources.Identifier;
import dev.craftmine.unlock.ServerPlayerUnlocks;
import dev.craftmine.server.TheGame;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import dev.craftmine.inventory.DoorMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.craftmine.item.component.RoomerinoComponentino;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import dev.craftmine.unlock.PlayerUnlock;
import dev.craftmine.unlock.UnlockCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.craftmine.registry.CraftmineDataComponents;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements CraftminePlayerDuck {
    @org.spongepowered.asm.mixin.Shadow
    private net.minecraft.server.MinecraftServer server;

    @Unique
    private ServerPlayerUnlocks craftmine$playerUnlocks;
    @Unique
    private dev.craftmine.inventory.PlayerHubInventory craftmine$hubInventory;
    @Unique
    private boolean craftmine$revisiting;
    @Unique
    private List<ItemStack> craftmine$pendingHubRewards = new ArrayList<ItemStack>();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$initFields(CallbackInfo ci) {
        // Mixin does not merge field initializers on this class-file version.
        if (this.craftmine$pendingHubRewards == null) this.craftmine$pendingHubRewards = new ArrayList<ItemStack>();
    }

    /*
     * Persist the per-player craftmine state (hub inventory stash, revisiting
     * flag, pending hub rewards) into the player data. These live in @Unique
     * mixin fields on the ServerPlayer INSTANCE, and the instance is silently
     * replaced in two everyday situations:
     *
     *  1. the config-phase client resync on mine entry (leaveForMine ->
     *     CraftmineClientResync): switchToConfig() saves + removes the player
     *     and placeNewPlayer later builds a FRESH ServerPlayer from the saved
     *     data - without this save/load, swapInventoryToHub() on exit restored
     *     from a brand-new empty stash and every hub item the player carried
     *     into their first mine of a session was deleted;
     *  2. any disconnect/relog (or server restart) while inside a mine.
     *
     * Both hooks sit at TAIL of the ServerPlayer overrides so they run after
     * vanilla has written/read the regular inventory.
     */
    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V", at = @At("TAIL"))
    private void craftmine$saveHubState(net.minecraft.world.level.storage.ValueOutput output, CallbackInfo ci) {
        this.craftmine$hubInventory().save(output, "craftmine_hub_inventory");
        if (this.craftmine$revisiting) {
            output.putBoolean("craftmine_revisiting", true);
        }
        if (!this.craftmine$pendingHubRewards.isEmpty()) {
            net.minecraft.world.level.storage.ValueOutput.ValueOutputList list = output.childrenList("craftmine_pending_hub_rewards");
            for (ItemStack itemStack : this.craftmine$pendingHubRewards) {
                list.addChild().store("Item", ItemStack.CODEC, itemStack);
            }
        }
    }

    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V", at = @At("TAIL"))
    private void craftmine$loadHubState(net.minecraft.world.level.storage.ValueInput input, CallbackInfo ci) {
        this.craftmine$hubInventory().load(input, "craftmine_hub_inventory");
        this.craftmine$revisiting = input.getBooleanOr("craftmine_revisiting", false);
        this.craftmine$pendingHubRewards.clear();
        for (net.minecraft.world.level.storage.ValueInput entry : input.childrenListOrEmpty("craftmine_pending_hub_rewards")) {
            entry.read("Item", ItemStack.OPTIONAL_CODEC).ifPresent(this.craftmine$pendingHubRewards::add);
        }
    }

    /*
     * Belt-and-braces spawn fix (the primary hook is PlayerSpawnFinderMixin —
     * 26.3's login PrepareSpawnTask calls findSpawn directly). Wherever the
     * fork's adjustSpawnLocation path runs with the hub default as the
     * suggestion, return it unchanged: the snapshot has no spawn finder.
     */
    @Inject(method = "adjustSpawnLocation", at = @At("HEAD"), cancellable = true)
    private void craftmine$keepHubSpawn(ServerLevel level, BlockPos spawnSuggestion, CallbackInfoReturnable<BlockPos> cir) {
        if (TheGame.DEFAULT_SPAWN.equals(spawnSuggestion)) {
            cir.setReturnValue(spawnSuggestion);
        }
    }

    @Unique
    private ServerPlayerUnlocks craftmine$unlocks() {
        if (this.craftmine$playerUnlocks == null) {
            ServerPlayer self = (ServerPlayer) (Object) this;
            net.minecraft.server.MinecraftServer server = this.server;
            java.nio.file.Path path = ((dev.craftmine.mixin.MinecraftServerAccessor) server).craftmine$storageSource()
                .getLevelPath(new net.minecraft.world.level.storage.LevelResource("unlocks"))
                .resolve(self.getStringUUID() + ".json");
            this.craftmine$playerUnlocks = new ServerPlayerUnlocks(server.getFixerUpper(), server.getPlayerList(), path, self);
        }
        return this.craftmine$playerUnlocks;
    }

    @Unique
    private dev.craftmine.inventory.PlayerHubInventory craftmine$hubInventory() {
        if (this.craftmine$hubInventory == null) {
            this.craftmine$hubInventory = new dev.craftmine.inventory.PlayerHubInventory(((ServerPlayer) (Object) this).getInventory().getContainerSize());
        }
        return this.craftmine$hubInventory;
    }

    @Override
    public TheGame craftmine$theGame() {
        return CraftmineServerHooks.theGame();
    }

    @Override
    public void addHubReward(ItemStack itemStack) {
        this.craftmine$pendingHubRewards.add(itemStack);
    }

    @Override
    public void swapInventoryToHub() {
        ServerPlayer self = (ServerPlayer) (Object) this;
        var inventory = self.getInventory();
        inventory.clearContent();
        for (int i = 0; i < this.craftmine$hubInventory().getContainerSize(); ++i) {
            ItemStack itemStack = this.craftmine$hubInventory().getItem(i);
            inventory.setItem(i, itemStack);
        }
        this.craftmine$hubInventory().clearContent();
        this.craftmine$pendingHubRewards.forEach(inventory::add);
        this.craftmine$pendingHubRewards.clear();
    }

    @Override
    public void swapInventoryFromHub() {
        ServerPlayer self = (ServerPlayer) (Object) this;
        this.craftmine$hubInventory().clearContent();
        for (int i = 0; i < self.getInventory().getContainerSize(); ++i) {
            ItemStack itemStack = self.getInventory().getItem(i);
            this.craftmine$hubInventory().setItem(i, itemStack);
        }
        self.getInventory().clearContent();
    }

    @Override
    public void makeVisible(Holder<PlayerUnlock> holder) {
        this.craftmine$unlocks().makeVisible(holder);
    }

    @Override
    public boolean isUnlocked(Holder<PlayerUnlock> holder) {
        return this.craftmine$unlocks().isUnlocked(holder);
    }

    @Override
    public boolean isActive(Holder<PlayerUnlock> holder) {
        return this.craftmine$unlocks().isActive(holder);
    }

    @Override
    public boolean buyUnlock(Holder<PlayerUnlock> holder) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (this.craftmine$unlocks().isVisibleAtAll(holder) && this.craftmine$baseBuyUnlock(holder)) {
            self.experienceLevel -= holder.value().unlockPrice();
            if (self.experienceLevel < 0) {
                self.experienceLevel = 0;
                self.experienceProgress = 0.0f;
                self.totalExperience = 0;
            }
            this.forceUnlock(holder);
            CraftmineTriggers.PLAYER_UNLOCK_BOUGHT.trigger(self);
            self.lastSentExp = -1;
            return true;
        }
        return false;
    }

    @Unique
    private boolean craftmine$baseBuyUnlock(Holder<PlayerUnlock> holder) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.experienceLevel < holder.value().unlockPrice() || this.isUnlocked(holder)) {
            return false;
        }
        if (holder.value().parent().isPresent()) {
            Holder<PlayerUnlock> parent = holder.value().parent().get();
            return this.isUnlocked(parent);
        }
        return true;
    }

    @Override
    public void reactivateUnlock(Holder<PlayerUnlock> holder) {
        if (this.craftmine$unlocks().isVisibleAtAll(holder)) {
            this.craftmine$unlocks().reactivateUnlock(holder);
        }
    }

    @Override
    public boolean relockUnlock(Holder<PlayerUnlock> holder) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        dev.craftmine.server.CraftmineRelock.Result result = dev.craftmine.server.CraftmineRelock.check(self, holder);
        if (result != dev.craftmine.server.CraftmineRelock.Result.OK) {
            if (result.messageKey() != null) {
                self.sendOverlayMessage(Component.translatable(result.messageKey()).withStyle(net.minecraft.ChatFormatting.RED));
            }
            return false;
        }
        int refund = dev.craftmine.server.CraftmineRelock.refund(self, holder);
        if (!this.craftmine$unlocks().relock(holder)) {
            return false;
        }
        if (refund > 0) {
            self.giveExperienceLevels(refund);
        }
        self.lastSentExp = -1;
        return true;
    }

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void craftmine$onPlayerDiedDispatch(net.minecraft.world.damagesource.DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        UnlockCondition.onPlayerDied(self.level(), self, damageSource);
        // Task 60 (fork asu.a(bwt), deobfuscated): "!isRevisiting && (hardcore ||
        // serverLevel().isMine()) -> serverLevel().mineData().addDeadPlayer(uuid);
        // if (hardcore) overworld().mineData().addDeadPlayer(uuid)". This is the
        // recording half of mine loss: ServerLevelMixin.handleInProgressMine fails
        // the mine (craftmine$handleMineLoss -> LEVEL_FAILED + markMineCompleted
        // (false)) once every player inside is recorded dead, and the mine crafter
        // then stamps mine_completed=false onto the item (red glint).
        dev.craftmine.duck.CraftminePlayerDuck craftminePlayerDuck = (dev.craftmine.duck.CraftminePlayerDuck) (Object) self;
        dev.craftmine.duck.CraftmineLevelDuck craftmineLevelDuck = (dev.craftmine.duck.CraftmineLevelDuck) (Object) self.level();
        boolean bl = CraftmineServerHooks.theGame().isHardcore();
        if (!craftminePlayerDuck.isRevisiting() && (bl || craftmineLevelDuck.isMine())) {
            craftmineLevelDuck.mineData().addDeadPlayer(self.getUUID());
            if (bl) {
                ((dev.craftmine.duck.CraftmineLevelDuck) (Object) CraftmineServerHooks.theGame().overworld()).mineData().addDeadPlayer(self.getUUID());
            }
        }
    }

    /*
     * FT-8 gameplay layer - THORNS_PLUS_PLUS unlock + SOUL_LINK world effect.
     * Fork ServerPlayer.hurtServer (bytecode-verified): after the
     * invulnerability/ PvP checks, BEFORE onPlayerTookDamage + super.hurtServer:
     *   - THORNS_PLUS_PLUS && random.nextBoolean() -> 6.0f explosion at the
     *     player's feet (no fire, MOB interaction, null source)
     *   - SOUL_LINK -> every OTHER player in the level takes the same damage
     *     via magic. NOTE: the fork's bytecode has NO re-entrancy guard, so
     *     with 2+ players it recurses A->B->A->B forever (StackOverflowError
     *     before any damage lands). The port adds a per-player guard so the
     *     linked damage applies exactly once per hurt event - documented
     *     deviation that preserves the fork's intent.
     */
    @Unique
    private boolean craftmine$soulLinkDispatching;

    @Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", shift = At.Shift.BEFORE))
    private void craftmine$thornsAndSoulLink(ServerLevel serverLevel, net.minecraft.world.damagesource.DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (dev.craftmine.duck.CraftmineDuckBridge.isActive(self, dev.craftmine.unlock.PlayerUnlocks.THORNS_PLUS_PLUS)
            && self.getRandom().nextBoolean()) {
            self.level().explode(null,
                net.minecraft.world.level.Explosion.getDefaultDamageSource(self.level(), self),
                null,
                self.getX(), self.getY(0.0625), self.getZ(),
                6.0F, false, net.minecraft.world.level.Level.ExplosionInteraction.MOB);
        }

        if (!this.craftmine$soulLinkDispatching
            && dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, dev.craftmine.mines.WorldEffects.SOUL_LINK)) {
            this.craftmine$soulLinkDispatching = true;
            try {
                for (ServerPlayer other : serverLevel.players()) {
                    if (other != self) {
                        other.hurtServer(serverLevel, self.damageSources().magic(), f);
                    }
                }
            } finally {
                this.craftmine$soulLinkDispatching = false;
            }
        }
    }


    @Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", shift = At.Shift.BEFORE))
    private void craftmine$onPlayerTookDamageDispatch(ServerLevel serverLevel, net.minecraft.world.damagesource.DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> cir) {
        UnlockCondition.onPlayerTookDamage(serverLevel, (ServerPlayer) (Object) this, damageSource, f);
    }

    @Override
    public void forceUnlock(Holder<PlayerUnlock> holder) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        this.craftmine$unlocks().unlock(holder);
        UnlockCondition.onUnlockedPlayerUnlock(self.level(), self, holder);
    }

    @Override
    public boolean donateExperienceToMineCrafter() {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!this.craftmine$canDonateExperienceToMineCrafter()) {
            return false;
        }
        int n = 20;
        while (n > 0) {
            int n2 = (int)(self.experienceProgress * (float)self.getXpNeededForNextLevel());
            if (n2 >= n) {
                self.setExperiencePoints(n2 - n);
                n = 0;
                continue;
            }
            n -= n2;
            self.setExperienceLevels(self.experienceLevel - 1);
            self.setExperiencePoints(getXpNeededForLevel(self.experienceLevel));
        }
        ((CraftmineLevelDuck) (Object) self.level()).addExperienceToMineCrafter(20);
        return true;
    }

    @Unique
    private boolean craftmine$canDonateExperienceToMineCrafter() {
        ServerPlayer self = (ServerPlayer) (Object) this;
        return this.getTotalExperienceBasedOnLevels() >= 20;
    }

    @Override
    public void onMineEntered() {
        ServerPlayer self = (ServerPlayer) (Object) this;
        self.getStats().setValue(self, net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST), 0);
        self.getFoodData().setFoodLevel(20);
        self.setHealth(self.getMaxHealth());
        this.craftmine$unlocks().onMineEnter();
    }

    @Override
    public boolean isRevisiting() {
        return this.craftmine$revisiting;
    }

    @Override
    public void setRevisiting(boolean bl) {
        this.craftmine$revisiting = bl;
    }

    @Override
    public void openDoor(ItemStack itemStack, BlockPos blockPos, Direction direction, DoubleBlockHalf doubleBlockHalf) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isSpectator()) {
            self.sendOverlayMessage(Component.translatable("container.spectatorCantOpen").withStyle(net.minecraft.ChatFormatting.RED));
        }
        if (self.containerMenu != self.inventoryMenu) {
            self.doCloseContainer();
        }
        self.nextContainerCounter();
        RoomerinoComponentino roomerinoComponentino = itemStack.get(dev.craftmine.registry.CraftmineDataComponents.ROOM);
        List<DoorMenu.Layout> list2;
        List<DoorMenu.PlacementInfo> list;
        if (roomerinoComponentino == null) {
            list2 = List.of();
            list = null;
        } else {
            var extracted = DoorMenu.extractLayout(self.level(), blockPos, direction, doubleBlockHalf, roomerinoComponentino);
            list2 = extracted.stream().map(java.util.function.Function.identity()).map(pair -> pair.getFirst()).toList();
            list = extracted.stream().map(pair -> pair.getSecond()).toList();
        }
        DoorMenu doorMenu = new DoorMenu(self.containerCounter, self.getInventory(), ContainerLevelAccess.create(self.level(), blockPos), list2, list);
        doorMenu.setKey(itemStack.split(1));
        self.connection.send(new ClientboundOpenDoorPacket(doorMenu.containerId, list2));
        self.initMenu(doorMenu);
        self.containerMenu = doorMenu;
    }

    @Override
    public ItemEntity craftmine$drop(ItemStack itemStack, boolean bl, boolean bl2, boolean bl3) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        return self.drop(itemStack, bl2, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    @Override
    public ServerPlayerUnlocks craftmine$playerUnlocks() {
        return this.craftmine$unlocks();
    }

    @Override
    public void addOrDropItem(ItemStack itemStack) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.getInventory().add(itemStack)) {
            return;
        }
        ItemEntity itemEntity = self.drop(itemStack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        if (itemEntity != null) {
            itemEntity.setNoPickUpDelay();
            itemEntity.setTarget(self.getUUID());
        }
    }

    @Override
    public int getTotalExperienceBasedOnLevels() {
        ServerPlayer self = (ServerPlayer) (Object) this;
        int n = (int)((float)self.getXpNeededForNextLevel() * self.experienceProgress);
        int n2 = 0;
        for (int i = 0; i < self.experienceLevel; ++i) {
            n2 += getXpNeededForLevel(i);
        }
        return n2 + n;
    }

    @Unique
    private static int getXpNeededForLevel(int n) {
        if (n >= 30) {
            return 112 + (n - 30) * 9;
        }
        if (n >= 15) {
            return 37 + (n - 15) * 5;
        }
        return 7 + Math.max(0, n) * 2;
    }

    @Override
    public void craftmine$tickUnlocks() {
        ServerPlayer self = (ServerPlayer) (Object) this;
        this.craftmine$unlocks().flushDirty(self);
    }

    /*
     * FT-8 gameplay layer - EXPLOSIVE_TRAPS world effect. Fork
     * ServerPlayer.tick (after updatePlayerAttributes, before the advancement/
     * unlock flushes): while standing on ground in a mine with the effect
     * active, a 1/400 chance per tick primes a TNT block at the feet with the
     * fork's 40-tick fuse and clears the block.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void craftmine$explosiveTraps(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Level level = self.level();
        if (self.onGround()
            && level instanceof ServerLevel serverLevel
            && dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, dev.craftmine.mines.WorldEffects.EXPLOSIVE_TRAPS)
            && self.getRandom().nextInt(400) == 0) {
            BlockPos below = self.blockPosition().below();
            if (craftmine$primeTnt(serverLevel, below)) {
                serverLevel.removeBlock(below, false);
            }
        }
    }

    @Unique
    private static boolean craftmine$primeTnt(ServerLevel serverLevel, BlockPos blockPos) {
        // inline of fork TntBlock.prime(level, pos, null, 40) - 26.3's
        // TntBlock.prime lost the fuse parameter, and the fork used 40 ticks
        if (!Boolean.TRUE.equals(serverLevel.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.TNT_EXPLODES))) {
            return false;
        }
        net.minecraft.world.entity.item.PrimedTnt primedTnt = new net.minecraft.world.entity.item.PrimedTnt(serverLevel,
            blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, null);
        primedTnt.setFuse(40);
        serverLevel.addFreshEntity(primedTnt);
        serverLevel.playSound(null, primedTnt.getX(), primedTnt.getY(), primedTnt.getZ(),
            net.minecraft.sounds.SoundEvents.TNT_PRIMED, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        serverLevel.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.PRIME_FUSE, blockPos);
        return true;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void craftmine$flushUnlocks(CallbackInfo ci) {
        this.craftmine$tickUnlocks();
        // Task 31: keep the inventory menu's unlock-driven slot flags fresh
        // server-side (the fork only updated them on the client screen tick;
        // without this the server would keep accepting clicks on locked
        // crafting grid slots until the next client-driven state arrived).
        if (self().containerMenu == self().inventoryMenu
                && self().inventoryMenu instanceof dev.craftmine.duck.CraftmineCraftingMenuDuck duck) {
            duck.craftmine$updateUnlockSlots();
        }
    }

    @Unique
    private ServerPlayer self() {
        return (ServerPlayer) (Object) this;
    }
}
