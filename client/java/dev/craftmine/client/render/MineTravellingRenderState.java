package dev.craftmine.client.render;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Per-frame render data for {@link MineTravellingBlockEntityRenderer}:
 * pulsing beam geometry/color, the animation clock, the block's age, and
 * the pre-extracted moving-block state for the spinning cube (null when
 * extraction ran on a non-client level).
 */
public class MineTravellingRenderState extends BlockEntityRenderState {
    public float beamScale;
    public int beamHeight;
    public int beamColor;
    public float animationTime;
    public float age;
    public @Nullable MovingBlockRenderState movingBlock;
}
