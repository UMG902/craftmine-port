/*
 * Ports craftmine additions to PrimaryLevelData: unlock storage, mine crafter levels,
 * level count, hub dimension type and mine events map.
 * Persistence of the new fields is injected into createTag (26.3 replaced setTag).
 */
package dev.craftmine.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import dev.craftmine.duck.CraftmineLevelDataDuck;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import dev.craftmine.block.entity.MineCrafterBlockEntity;
import net.minecraft.world.level.dimension.DimensionType;
import dev.craftmine.mines.MineEventData;
import dev.craftmine.mines.SpecialMine;
import dev.craftmine.mines.UnlockMode;
import dev.craftmine.mines.WorldEffect;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PrimaryLevelData.class)
public abstract class PrimaryLevelDataMixin implements CraftmineLevelDataDuck {
    @Unique
    private List<WorldEffect> craftmine$worldEffectsUnlocked = new ArrayList<WorldEffect>();
    @Unique
    private List<SpecialMine> craftmine$specialMinesUnlocked = new ArrayList<SpecialMine>();
    @Unique
    private List<SpecialMine> craftmine$specialMinesWon = new ArrayList<SpecialMine>();
    @Unique
    private int craftmine$mineCrafterLevel;
    @Unique
    private int craftmine$mineCrafterExp;
    @Unique
    private int craftmine$levelCount;
    @Unique
    private Map<ResourceKey<Level>, MineEventData> craftmine$events = new HashMap<ResourceKey<Level>, MineEventData>();
    @Unique
    private Optional<Holder<DimensionType>> craftmine$hubDimensionType = Optional.empty();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$initFields(CallbackInfo ci) {
        // Mixin does not merge field initializers on this class-file version;
        // initialize the duck state explicitly at construction time.
        if (this.craftmine$worldEffectsUnlocked == null) this.craftmine$worldEffectsUnlocked = new ArrayList<WorldEffect>();
        if (this.craftmine$specialMinesUnlocked == null) this.craftmine$specialMinesUnlocked = new ArrayList<SpecialMine>();
        if (this.craftmine$specialMinesWon == null) this.craftmine$specialMinesWon = new ArrayList<SpecialMine>();
        if (this.craftmine$events == null) this.craftmine$events = new HashMap<ResourceKey<Level>, MineEventData>();
        if (this.craftmine$hubDimensionType == null) this.craftmine$hubDimensionType = Optional.empty();
    }

    @Override
    public Map<ResourceKey<Level>, MineEventData> events() {
        return this.craftmine$events;
    }

    @Override
    public int incrementAndGetLevelCount() {
        return ++this.craftmine$levelCount;
    }

    @Override
    public int getLevelCount() {
        return this.craftmine$levelCount;
    }

    @Override
    public void unlockEffect(WorldEffect worldEffect) {
        this.craftmine$worldEffectsUnlocked.add(worldEffect);
    }

    @Override
    public Iterable<WorldEffect> getUnlockedEffects() {
        return this.craftmine$worldEffectsUnlocked;
    }

    @Override
    public boolean isSpecialMineUnlocked(SpecialMine specialMine) {
        return this.craftmine$specialMinesUnlocked.contains(specialMine);
    }

    @Override
    public void unlockSpecialMine(SpecialMine specialMine) {
        this.craftmine$specialMinesUnlocked.add(specialMine);
    }

    @Override
    public void mineCompleted(Optional<SpecialMine> optional, boolean bl) {
        if (bl && optional.isPresent()) {
            this.craftmine$specialMinesWon.add(optional.get());
        }
    }

    @Override
    public Optional<SpecialMine> getNextSpecialMine(RandomSource randomSource) {
        if (!this.craftmine$isSpecialMineLevel(this.craftmine$levelCount + 1)) {
            return Optional.empty();
        }
        // Unoriginal feature gate (defense in depth): the boss mines' unlock
        // conditions already check the config, but a world unlocked while the switch was on
        // must stop rolling them the moment the master switch or the per-boss sub-toggle
        // goes off again.
        java.util.function.Predicate<SpecialMine> unoriginalGate = specialMine -> switch (specialMine.key()) {
            case "elder_guardian_boss" -> dev.craftmine.CraftmineConfig.guardianBoss();
            case "breeze_boss" -> dev.craftmine.CraftmineConfig.breezeBoss();
            default -> true;
        };
        // Roll pool = the always-rollable default mines (registered with no unlock
        // condition: enderman_boss, kuiper_belt - SpecialMine.Builder.register puts
        // them in SpecialMines.DEFAULT_UNLOCKED) UNION every condition mine unlocked
        // so far. Query-time union, mirroring isEffectUnlocked's ALWAYS_UNLOCKED
        // branch below: no save-format change, so fresh worlds AND existing saves
        // pick it up immediately. Without the defaults, a world whose player has not
        // unlocked any condition mine yet (the normal case - boss mines need
        // skeleton/pillager kills, wither skulls, mace smashes...) reaches the
        // every-5th-level craft with an EMPTY pool and Util.getRandom(list, random)
        // throws "IllegalArgumentException: Bound must be positive" from
        // BitRandomSource.nextInt(0): the mine crafter menu cannot open at all
        // (user report 2026-10-30, after batch AC).
        List<SpecialMine> list = Stream.concat(
            dev.craftmine.mines.SpecialMines.DEFAULT_UNLOCKED.stream(),
            this.craftmine$specialMinesUnlocked.stream()
        ).filter(unoriginalGate)
         .filter(specialMine -> !this.craftmine$specialMinesWon.contains(specialMine)).toList();
        if (list.isEmpty()) {
            // every candidate already won -> repeat them (snapshot behavior)
            list = Stream.concat(
                dev.craftmine.mines.SpecialMines.DEFAULT_UNLOCKED.stream(),
                this.craftmine$specialMinesUnlocked.stream()
            ).filter(unoriginalGate).toList();
        }
        if (list.isEmpty()) {
            // last-resort guard: an empty pool must never crash the menu open
            // (only possible if the special-mine bootstrap itself did not run)
            return Optional.empty();
        }
        return Optional.of(Util.getRandom(list, randomSource));
    }

    @Unique
    private boolean craftmine$isSpecialMineLevel(int n) {
        return n % 5 == 0;
    }

    @Override
    public boolean isEffectUnlocked(WorldEffect worldEffect) {
        // The ten unlockedByDefault() starter effects (surface world, surface exits,
        // plains, savannas, sheep, cows, pigs, chickens, frogs, base stone) are always
        // unlocked - this is what pre-fills the Mine Crafter's "Mine Effects" drawer on
        // a fresh world (the CFR decompile lost this branch, same loss pattern as
        // WorldEffect.canRandomize). Query-time check: no save-format change, existing
        // saves pick it up immediately.
        if (worldEffect.unlockMode() == UnlockMode.ALWAYS_UNLOCKED) {
            return true;
        }
        return this.craftmine$worldEffectsUnlocked.contains(worldEffect);
    }

    @Override
    public Optional<Holder<DimensionType>> hubDimensionType() {
        return this.craftmine$hubDimensionType;
    }

    @Override
    public void setHubDimensionType(Holder<DimensionType> holder) {
        this.craftmine$hubDimensionType = Optional.of(holder);
    }

    @Override
    public int getMineCrafterLevel() {
        return this.craftmine$mineCrafterLevel;
    }

    @Override
    public int getMineCrafterExp() {
        return this.craftmine$mineCrafterExp;
    }

    @Override
    public void addExperienceToMineCrafter(int n) {
        this.craftmine$mineCrafterExp += n;
        while (this.craftmine$mineCrafterExp >= MineCrafterBlockEntity.experienceRequiredForLevel(this.craftmine$mineCrafterLevel)) {
            this.craftmine$mineCrafterExp -= MineCrafterBlockEntity.experienceRequiredForLevel(this.craftmine$mineCrafterLevel);
            ++this.craftmine$mineCrafterLevel;
        }
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "createTag", at = @At("RETURN"))
    private CompoundTag craftmine$saveMineData(CompoundTag compoundTag, UUID uUID) {
        this.craftmine$hubDimensionType.ifPresent(holder -> compoundTag.store("hub_dimension_type", DimensionType.CODEC, holder));
        compoundTag.store("effects_unlocked", WorldEffect.CODEC.listOf(), this.craftmine$worldEffectsUnlocked);
        compoundTag.store("special_mines_unlocked", SpecialMine.CODEC.listOf(), this.craftmine$specialMinesUnlocked);
        compoundTag.store("won_special_mines_won", SpecialMine.CODEC.listOf(), this.craftmine$specialMinesWon);
        compoundTag.putInt("mine_crafter_level", this.craftmine$mineCrafterLevel);
        compoundTag.putInt("mine_crafter_exp", this.craftmine$mineCrafterExp);
        compoundTag.putInt("level_count", this.craftmine$levelCount);
        return compoundTag;
    }

    @Override
    public void loadPersistedMineData(com.mojang.serialization.Dynamic<?> input) {
        this.craftmine$levelCount = input.get("level_count").asInt(0);
        this.craftmine$mineCrafterLevel = input.get("mine_crafter_level").asInt(0);
        this.craftmine$mineCrafterExp = input.get("mine_crafter_exp").asInt(0);
        this.craftmine$worldEffectsUnlocked.clear();
        this.craftmine$worldEffectsUnlocked.addAll(
            input.get("effects_unlocked").read(WorldEffect.CODEC.listOf()).result().orElse(List.of())
        );
        this.craftmine$specialMinesUnlocked.clear();
        this.craftmine$specialMinesUnlocked.addAll(
            input.get("special_mines_unlocked").read(SpecialMine.CODEC.listOf()).result().orElse(List.of())
        );
        this.craftmine$specialMinesWon.clear();
        this.craftmine$specialMinesWon.addAll(
            input.get("won_special_mines_won").read(SpecialMine.CODEC.listOf()).result().orElse(List.of())
        );
        this.craftmine$hubDimensionType = input.get("hub_dimension_type")
            .read(DimensionType.CODEC)
            .result()
            .map(holder -> (Holder<DimensionType>)holder);
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "parse", at = @At("RETURN"))
    private static PrimaryLevelData craftmine$loadMineData(
        PrimaryLevelData original,
        com.mojang.serialization.Dynamic<?> input,
        net.minecraft.world.level.LevelSettings settings,
        net.minecraft.world.level.storage.PrimaryLevelData.SpecialWorldProperty specialWorldProperty,
        com.mojang.serialization.Lifecycle lifecycle
    ) {
        ((CraftmineLevelDataDuck)(Object)original).loadPersistedMineData(input);
        return original;
    }
}
