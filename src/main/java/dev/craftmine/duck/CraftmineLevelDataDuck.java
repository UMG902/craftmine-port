package dev.craftmine.duck;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import dev.craftmine.mines.MineEventData;
import dev.craftmine.mines.SpecialMine;
import dev.craftmine.mines.WorldEffect;

/**
 * Duck interface for craftmine additions to PrimaryLevelData (and WorldData.events()).
 */
public interface CraftmineLevelDataDuck {
    Map<ResourceKey<Level>, MineEventData> events();

    int incrementAndGetLevelCount();

    int getLevelCount();

    void unlockEffect(WorldEffect worldEffect);

    Iterable<WorldEffect> getUnlockedEffects();

    boolean isSpecialMineUnlocked(SpecialMine specialMine);

    void unlockSpecialMine(SpecialMine specialMine);

    void mineCompleted(Optional<SpecialMine> specialMine, boolean won);

    Optional<SpecialMine> getNextSpecialMine(RandomSource randomSource);

    boolean isEffectUnlocked(WorldEffect worldEffect);

    Optional<Holder<DimensionType>> hubDimensionType();

    void setHubDimensionType(Holder<DimensionType> holder);

    /**
     * Restores the persisted mine state (level count, unlocked effects, mine crafter
     * level/exp) from the level.dat dynamic — the load counterpart of the fields
     * written by the createTag mixin. Without this the level count resets on every
     * restart and a newly crafted mine re-uses level1, overwriting the old datapack.
     */
    void loadPersistedMineData(com.mojang.serialization.Dynamic<?> input);

    int getMineCrafterLevel();

    int getMineCrafterExp();

    void addExperienceToMineCrafter(int amount);

    @Nullable
    ServerPlayer hubPlayerListener();
}
