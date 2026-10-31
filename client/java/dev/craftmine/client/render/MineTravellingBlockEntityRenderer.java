package dev.craftmine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import dev.craftmine.block.entity.MineTravellingBlockEntity;
import dev.craftmine.registry.CraftmineContent;
import org.jspecify.annotations.Nullable;

/**
 * Renders the mine travelling block: a slowly rotating bedrock-ish cube
 * shooting a periodic beacon beam into the sky (blue for the mine exit,
 * light blue while the mine is still spawning), so the player can spot
 * where they came in from anywhere in the mine.
 */
public class MineTravellingBlockEntityRenderer
    implements BlockEntityRenderer<MineTravellingBlockEntity, MineTravellingRenderState> {

    private static final Identifier BEAM_TEXTURE = Identifier.withDefaultNamespace("textures/entity/end_portal/end_gateway_beam.png");
    /** Beam half-widths handed to the beacon beam renderer. */
    private static final float BEAM_RADIUS_INNER = 0.15F;
    private static final float BEAM_RADIUS_OUTER = 0.175F;
    /** Beam length while the mine is live (blocks above the block). */
    private static final int EXIT_BEAM_HEIGHT = 50;
    /** Cube spin, radians per second of age. */
    private static final double SPIN_RADIANS_PER_AGE = 0.1;
    /** The pulsing beam cycle length, in game ticks. */
    private static final int BEAM_CYCLE_TICKS = 40;

    public MineTravellingBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // 26.3 submission-based rendering resolves everything per-submit, so the
        // context is not stored - but the provider shape requires it
        // (BlockEntityRenderers.register uses MineTravellingBlockEntityRenderer::new).
    }

    @Override
    public MineTravellingRenderState createRenderState() {
        return new MineTravellingRenderState();
    }

    @Override
    public void extractRenderState(MineTravellingBlockEntity block, MineTravellingRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(block, state, partialTick, cameraPos, crumbling);
        Level level = block.getLevel();
        if (level == null) {
            return;
        }
        // Pulse shape: sin(pi * periodicallyBeam) gives a smooth out-and-back scale.
        float pulse = Mth.sin(block.periodicallyBeam(partialTick) * (float) Math.PI);
        int beamHeight = Mth.floor(pulse * (block.isSpawning() ? (double) level.getMaxY() : EXIT_BEAM_HEIGHT));
        state.beamScale = pulse;
        state.beamHeight = beamHeight;
        state.beamColor = block.isSpawning()
            ? net.minecraft.world.item.DyeColor.LIGHT_BLUE.getTextureDiffuseColor()
            : net.minecraft.world.item.DyeColor.BLUE.getTextureDiffuseColor();
        state.animationTime = (float) Math.floorMod(level.getGameTime(), BEAM_CYCLE_TICKS) + partialTick;
        state.age = block.age() + partialTick;
        if (level instanceof ClientLevel clientLevel) {
            state.movingBlock = extractMovingBlock(clientLevel, block.getBlockPos());
        }
    }

    private static MovingBlockRenderState extractMovingBlock(ClientLevel level, BlockPos pos) {
        BlockState blockState = CraftmineContent.MINE_TRAVELLING_BLOCK.defaultBlockState();
        MovingBlockRenderState moving = new MovingBlockRenderState();
        moving.randomSeedPos = pos;
        moving.blockPos = pos;
        moving.blockState = blockState;
        moving.biome = level.getBiome(pos);
        moving.cardinalLighting = level.cardinalLighting();
        moving.lightEngine = level.getLightEngine();
        return moving;
    }

    @Override
    public void submit(MineTravellingRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BeaconRenderer.submitBeaconBeam(poseStack, collector, BEAM_TEXTURE,
            state.beamScale, state.animationTime,
            -state.beamHeight, state.beamHeight * 2,
            state.beamColor, BEAM_RADIUS_INNER, BEAM_RADIUS_OUTER);
        if (state.movingBlock != null) {
            poseStack.pushPose();
            double spin = state.age * SPIN_RADIANS_PER_AGE;
            poseStack.rotateAround(Axis.YP.rotation((float) (spin % (Math.PI * 2))), 0.5F, 0.0F, 0.5F);
            collector.submitMovingBlock(poseStack, state.movingBlock, 0);
            poseStack.popPose();
        }
    }

    @Override
    public int getViewDistance() {
        return 256; // the beam must be visible across the whole mine
    }
}
