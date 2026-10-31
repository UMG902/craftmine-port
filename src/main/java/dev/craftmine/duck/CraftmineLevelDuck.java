package dev.craftmine.duck;

import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import dev.craftmine.mines.MineData;
import dev.craftmine.mines.MineEvent;
import dev.craftmine.mines.SpecialMine;
import dev.craftmine.mines.WorldEffect;
import net.minecraft.world.phys.Vec3;
import dev.craftmine.level.DimensionSpecialEffects;

/**
 * Duck interface for the craftmine additions to ServerLevel.
 * Implemented via ServerLevelMixin; ported classes access these via
 * CraftmineDuckBridge casts.
 */
public interface CraftmineLevelDuck {
    default boolean isMine() { return false; }

    default boolean isSpecialMine() { return false; }

    default java.util.Optional<SpecialMine> specialMine() { return java.util.Optional.empty(); }

    default boolean isMineCompleted() { return false; }

    default boolean isMineWon() { return false; }

    default void markMineCompleted(boolean win) { }

    default void toggledMineTravellingBlock(BlockPos blockPos) { }

    default void unlockEffect(WorldEffect worldEffect) { }

    default boolean isEffectUnlocked(WorldEffect worldEffect) { return false; }

    default boolean isActive(WorldEffect worldEffect) { return false; }

    default List<WorldEffect> getActiveEffects() { return List.of(); }

    default List<WorldEffect> getUnlockedEffects() { return List.of(); }

    default void dropUnlockEffect(Vec3 vec3, WorldEffect worldEffect, @Nullable ServerPlayer serverPlayer) { }

    default int getMineCrafterLevel() { return 0; }

    default int getMineCrafterExp() { return 0; }

    default void addExperienceToMineCrafter(int amount) { }

    default void unlockSpecialMine(SpecialMine specialMine) { }

    default boolean isSpecialMineUnlocked(SpecialMine specialMine) { return false; }

    default Optional<SpecialMine> getNextSpecialMine() { return Optional.empty(); }

    default void startEvent(MineEvent mineEvent) { }

    default List<MineEvent> events() { return List.of(); }

    default MineData mineData() { return null; }

    default void onMineEntered() { }

    default void onMineLeave() { }

    default void tickEvents() { }

    default void handleInProgressMine() { }

    default void leaveForMine(BlockPos blockPos, boolean revisit, Optional<java.util.UUID> playerFilter) { }

    default void teleportAllPlayersToMine(boolean revisit, Optional<java.util.UUID> playerFilter) { }

    default void dropRewards(BlockPos blockPos) { }

    default BlockPos craftmine$wardenArenaPos() { return BlockPos.ZERO; }

    default void craftmine$setWardenArenaPos(BlockPos pos) { }

    default BlockPos craftmine$dirtyIceBallPos() { return BlockPos.ZERO; }

    default void respawnPlayerIntoHub(ServerPlayer serverPlayer, java.util.function.Consumer<ServerPlayer> afterTeleport) { }

    default void handleMineWin(BlockPos blockPos) { }

    /** craftmine's Level.theGame(); may be null outside the game context. */
    default dev.craftmine.server.TheGame craftmine$theGame() { return null; }

    /**
     * The level's active sky (Dimension Control selection). Tri-state:
     * null = no override stored -> the caller falls back to the dimension's
     * default sky (see SkyDefaults); NothingSky.INSTANCE = the "nothing"
     * state (empty control slot) -> the client renders a BLACK sky; any
     * other Sky = that sky.
     */
    @Nullable
    default DimensionSpecialEffects.Sky craftmine$getActiveSky() { return null; }

    /**
     * Sets the active sky override. Null clears the override (level falls
     * back to its default); DimensionSpecialEffects.NothingSky.INSTANCE
     * stores the "nothing" (black sky) state. Server side persists +
     * broadcasts.
     */
    default void craftmine$setActiveSky(@Nullable DimensionSpecialEffects.Sky sky) { }

    /**
     * Skybox-gate mutation from the Dimension Control menu: swaps in the
     * rebuilt DimensionType holder while remembering the level's pre-sky
     * registration once, so a later cleared slot can restore it
     * (server side; the client mirrors via craftmine$setDimensionType).
     */
    default void craftmine$applySkyDimensionType(net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> holder) { }

    /** Restores the pre-sky dimension type registration (no-op if never applied). */
    default void craftmine$restorePreSkyDimensionType() { }

    /** craftmine's Level.setDimensionType. */
    void craftmine$setDimensionType(net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> holder);

    /**
     * Assigns the level's dimension type registration field directly (the
     * shadow lives on the declaring class - Level - so the client mixin can
     * route its swap through this instead of re-shadowing an inherited
     * field). Task 44.
     */
    default void craftmine$swapDimensionTypeRegistration(net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> holder) {
        this.craftmine$setDimensionType(holder);
    }

    /** craftmine's Level.dimensionTypeRegistration(). */
    net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> craftmine$dimensionTypeRegistration();
}
