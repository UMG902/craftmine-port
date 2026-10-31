package dev.craftmine.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Task 46: the craftmine snapshot (25w14craftmine) replaced the stronghold
 * portal room's end portal frames with plain DIAMOND BLOCKS and removed the
 * end portal itself - verified by diffing the snapshot's decompiled
 * StrongholdPieces.PortalRoom.postProcess against vanilla 1.21.5: all 12
 * frame ring positions place Blocks.DIAMOND_BLOCK, the eye-of-ender roll
 * loop and the allEyes -> END_PORTAL branch are deleted, and everything
 * else (lava, stairs, iron bars, silverfish spawner) is untouched.
 *
 * We mirror that exactly with two redirects inside postProcess:
 *  - any placeBlock handing an END_PORTAL_FRAME state places a plain
 *    diamond block instead. The FACING/HAS_EYE setValue chains run on
 *    genuine frame states before the call, so no missing-property crash;
 *  - the single nextFloat eye roll returns 0.0 (never > 0.9): no eyes,
 *    allEyes stays false, the END_PORTAL branch is dead code, and - like
 *    the snapshot, which deleted the loop - the RNG stream is not consumed
 *    (a @Redirect replaces the call, the original nextFloat never runs, so
 *    subsequent stronghold piece placement sees the same RNG stream the
 *    snapshot produces).
 */
@Mixin(StrongholdPieces.PortalRoom.class)
public abstract class StrongholdPortalRoomMixin {

    @Redirect(
        method = "postProcess",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/StrongholdPieces$PortalRoom;placeBlock(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/block/state/BlockState;IIILnet/minecraft/world/level/levelgen/structure/BoundingBox;)V"
        )
    )
    private static void craftmine$diamondPortalFrames(StrongholdPieces.PortalRoom instance, WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox box) {
        if (state.is(Blocks.END_PORTAL_FRAME)) {
            state = Blocks.DIAMOND_BLOCK.defaultBlockState();
        }
        instance.placeBlock(level, state, x, y, z, box);
    }

    @Redirect(
        method = "postProcess",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextFloat()F"
        )
    )
    private static float craftmine$noEndPortalEyes(RandomSource random) {
        return 0.0f;
    }
}
