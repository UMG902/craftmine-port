/*
 * Two server-level fixes:
 *
 * 1. setInitialSpawn: 26.3 boots through TheGame.adopt(), which wraps the
 *    already-initialized vanilla server and therefore never runs the port's
 *    TheGame.createLevels()/setInitialSpawn chain — the fork's own vanilla
 *    spawn search wins and the player materializes at a random spiral point
 *    instead of the snapshot's hub spawn. This mirrors the snapshot's
 *    TheGame.setInitialSpawn: fixed spawn 13 2 8, yaw 90 (facing west, into
 *    the hub), bonus chest preserved.
 *
 * 2. tickConnection: the client-resync state machine (see
 *    CraftmineClientResync) must tick while players are in the configuration
 *    phase. IntegratedServer pauses the whole tick when the player list is
 *    empty (exactly what a full reconfiguration causes) and its tickPaused()
 *    path never reaches tickServer/tickChildren — but tickConnection() runs
 *    on every path (unpaused, empty-pause, integrated-paused), so it is the
 *    one hook point that always fires.
 */
package dev.craftmine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.server.MinecraftServer;
import dev.craftmine.server.TheGame;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void craftmine$snapshotSpawn(ServerLevel level, ServerLevelData levelData, boolean spawnBonusChest, boolean isDebug, LevelLoadListener levelLoadListener, CallbackInfo ci) {
        levelData.setSpawn(LevelData.RespawnData.of(level.dimension(), TheGame.DEFAULT_SPAWN, TheGame.SPAWN_ANGLE, 0.0f));
        if (spawnBonusChest) {
            level.registryAccess()
                .lookup(Registries.FEATURE)
                .flatMap(registry -> registry.get(MiscOverworldFeatures.BONUS_CHEST))
                .ifPresent(feature -> feature.value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), levelData.getRespawnData().pos()));
        }
        ci.cancel();
    }

    @Inject(method = "tickConnection", at = @At("HEAD"))
    private void craftmine$tickResync(CallbackInfo ci) {
        dev.craftmine.server.CraftmineClientResync.tick((MinecraftServer) (Object) this);
    }

    /**
     * Task 64: apply deferred level registrations (live mine creation) at the
     * ONE point that is guaranteed to be outside every level-map iteration.
     * tickChildren's level loop, the connection phase, playerList.tick, the
     * chunk-send phase and serverActivityMonitor all precede this TAIL; and
     * tickServer performs no further level iteration after tickChildren
     * returns (verified against the 26.3 sources). Registering a new level
     * here can never throw the ConcurrentModificationException that the
     * immediate in-tick registration produced (crash report: entering the
     * first freshly-crafted mine of a session - LinkedHashMap$LinkedHashIterator
     * in MinecraftServer.tickChildren).
     */
    @Inject(method = "tickChildren", at = @At("TAIL"))
    private void craftmine$drainPendingLevelRegistrations(CallbackInfo ci) {
        dev.craftmine.server.CraftmineLevelRegistration.drain((MinecraftServer) (Object) this);
    }

    /**
     * Task 58: restore per-mine stem metadata (craftSeed / effects / mine /
     * spawn / exitTypes) from the world's generated datapacks BEFORE vanilla
     * createLevels() constructs the mine levels - ChunkMap's RandomState XORs
     * the seed with CraftmineLevelStemData.craftSeed, which was empty after a
     * restart and made every rejoined mine regenerate as a different seed.
     * Safe here: storageSource + registries fields are assigned earlier in the
     * ctor, and the custom registries were bootstrapped during worldStem load.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$restoreMineStemData(CallbackInfo ci) {
        dev.craftmine.registry.CraftmineLevelStemData.restoreFromWorldDir(
            ((MinecraftServerAccessor) (Object) this).craftmine$storageSource().getLevelPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR),
            ((MinecraftServerAccessor) (Object) this).craftmine$registries().compositeAccess()
        );
    }



}
