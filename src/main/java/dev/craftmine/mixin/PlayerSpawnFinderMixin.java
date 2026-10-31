/*
 * 26.3's login PrepareSpawnTask runs PlayerSpawnFinder.findSpawn directly on
 * the saved spawn suggestion — the snapshot has no spawn finder at all, so the
 * spiral search moves the player off the exact hub spawn (observed 9.5/9.5 and
 * 8.5/5.5 in live tests). When the suggestion IS the hub default (13 2 8),
 * return it unchanged: bottom-center of that block, yaw comes from the respawn
 * data separately.
 */
package dev.craftmine.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import dev.craftmine.server.TheGame;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerSpawnFinder.class)
public abstract class PlayerSpawnFinderMixin {

    @Inject(method = "findSpawn", at = @At("HEAD"), cancellable = true)
    private static void craftmine$keepHubSpawn(ServerLevel level, BlockPos spawnSuggestion, CallbackInfoReturnable<CompletableFuture<Vec3>> cir) {
        if (TheGame.DEFAULT_SPAWN.equals(spawnSuggestion)) {
            cir.setReturnValue(CompletableFuture.completedFuture(Vec3.atBottomCenterOf(spawnSuggestion)));
        }
    }
}
