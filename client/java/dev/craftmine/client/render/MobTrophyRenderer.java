package dev.craftmine.client.render;

import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.craftmine.block.MobTrophyBlock;
import dev.craftmine.block.entity.MobTrophyBlockEntity;
import dev.craftmine.item.MobTrophyInfo;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Renders the mob trophy block: a shrunken live-render of the trophy mob
 * standing on the block, optionally foil-glittering when the trophy is
 * "shiny" (the 1%-roll shiny from the trophy crafting).
 *
 * <p>Because 26.3 renders through submit collectors rather than immediate
 * drawing, the glint is applied by wrapping the collector in a
 * {@link FoilDelegate} that re-submits every model/geometry call once more
 * with the enchanted-glint render type.</p>
 */
public class MobTrophyRenderer implements BlockEntityRenderer<MobTrophyBlockEntity, MobTrophyRenderState> {

    /** Mobs lie about their size, so the fit-to-box scale gets halved. */
    public static final float EXTRA_SCALE_BECAUSE_MOBS_LIE_ABOUT_SIZE = 0.5F;
    public static final float BOX_WIDTH = 0.875F;
    public static final float BOX_HEIGHT = 0.6875F;
    /** Trophy base sits this high above the block bottom (1/16 block). */
    private static final float BASE_Y_OFFSET = 0.0625F;
    /** Minimum fitted scale, so tiny mobs stay visible. */
    private static final float MIN_SCALE = 0.1F;

    private static final RenderType SHINY_RENDER_TYPE = RenderTypes.entitySolidGlint(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM);

    private final EntityRenderDispatcher entityRenderer;

    public MobTrophyRenderer(BlockEntityRendererProvider.Context context) {
        this.entityRenderer = context.entityRenderer();
    }

    @Override
    public MobTrophyRenderState createRenderState() {
        return new MobTrophyRenderState();
    }

    @Override
    public void extractRenderState(MobTrophyBlockEntity trophy, MobTrophyRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(trophy, state, partialTick, cameraPos, crumbling);
        state.trophyInfo = trophy.getEntityType();
        state.facing = trophy.getBlockState().getValue(MobTrophyBlock.FACING);
        state.entityRenderState = null;
        if (state.trophyInfo == null) {
            return;
        }
        EntityRenderState mobState = extractEntityRenderState(this.entityRenderer, state.trophyInfo, trophy.getLevel(), partialTick);
        if (mobState == null) {
            return;
        }
        mobState.lightCoords = state.lightCoords;
        mobState.shadowPieces.clear();
        mobState.outlineColor = 0;
        state.entityRenderState = mobState;
    }

    @Override
    public void submit(MobTrophyRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.trophyInfo != null && state.entityRenderState != null) {
            render(poseStack, collector, state.trophyInfo, state.entityRenderState, this.entityRenderer, state.facing, camera);
        }
    }

    /**
     * Builds a throwaway render state for the trophy's mob type. Prefers a
     * real entity extraction (so AI-driven pose data is present); falls back
     * to a bare state when there is no level yet (UI previews).
     */
    public static @Nullable EntityRenderState extractEntityRenderState(EntityRenderDispatcher dispatcher, MobTrophyInfo info,
                                                                      @Nullable Level level, float partialTick) {
        EntityType<?> entityType = info.type().value();
        EntityRenderState mobState = new EntityRenderState();
        mobState.entityType = entityType;
        EntityRenderer<?, ?> renderer = dispatcher.getRenderer(mobState);
        if (renderer == null) {
            return null;
        }
        if (level != null) {
            Entity entity = entityType.create(level, EntitySpawnReason.LOAD);
            if (entity != null) {
                return dispatcher.extractEntity(entity, partialTick);
            }
        }
        return renderer.createRenderState();
    }

    /**
     * Fits the mob into the trophy box and submits it through (possibly
     * foil-wrapped) collectors.
     */
    public static void render(PoseStack poseStack, SubmitNodeCollector collector, MobTrophyInfo info,
                              EntityRenderState mobState, EntityRenderDispatcher dispatcher,
                              Direction facing, CameraRenderState camera) {
        EntityDimensions dimensions = info.type().value().getDimensions();
        float heightScale = BOX_HEIGHT / dimensions.height();
        float widthScale = BOX_WIDTH / dimensions.width();
        float scale = Math.max(MIN_SCALE, Math.min(heightScale, widthScale) * EXTRA_SCALE_BECAUSE_MOBS_LIE_ABOUT_SIZE);
        if (!Float.isFinite(scale)) {
            scale = 1.0F;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, BASE_Y_OFFSET, 0.5F);
        poseStack.rotate(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.scale(scale, scale, scale);
        SubmitNodeCollector wrapped = info.shiny() ? new FoilSubmitNodeCollector(collector) : collector;
        dispatcher.submit(mobState, camera, 0.0, 0.0, 0.0, poseStack, wrapped);
        poseStack.popPose();
    }

    /**
     * Collector decorator that submits everything twice: once normally and
     * once with the enchanted-glint render type. Shadow/name-tag/flame-style
     * calls pass through untouched.
     */
    private abstract static class FoilDelegate implements OrderedSubmitNodeCollector {
        protected final OrderedSubmitNodeCollector delegate;

        protected FoilDelegate(OrderedSubmitNodeCollector delegate) {
            this.delegate = delegate;
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                                    int light, int overlay, int tint, UvMapping uv, int outline) {
            this.delegate.submitModel(model, state, poseStack, renderType, light, overlay, tint, uv, outline);
            this.delegate.submitModel(model, state, poseStack, SHINY_RENDER_TYPE, light, overlay, tint, uv, outline);
        }

        @Override
        public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer geometry) {
            this.delegate.submitCustomGeometry(poseStack, renderType, geometry);
            this.delegate.submitCustomGeometry(poseStack, SHINY_RENDER_TYPE, geometry);
        }

        @Override
        public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
            this.delegate.submitShadow(poseStack, radius, pieces);
        }

        @Override
        public void submitNameTag(PoseStack poseStack, Vec3 offset, int light, Component label, boolean seeThrough, int background, CameraRenderState camera) {
            this.delegate.submitNameTag(poseStack, offset, light, label, seeThrough, background, camera);
        }

        @Override
        public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean dropShadow,
                               Font.DisplayMode mode, int light, int background, int color, int outline) {
            this.delegate.submitText(poseStack, x, y, text, dropShadow, mode, light, background, color, outline);
        }

        @Override
        public void submitTextBackground(PoseStack poseStack, float x1, float y1, float x2, float y2,
                                         int z, Font.DisplayMode mode, int color) {
            this.delegate.submitTextBackground(poseStack, x1, y1, x2, y2, z, mode, color);
        }

        @Override
        public void submitFlame(PoseStack poseStack, EntityRenderState state, Quaternionf rotation) {
            this.delegate.submitFlame(poseStack, state, rotation);
        }

        @Override
        public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leash) {
            this.delegate.submitLeash(poseStack, leash);
        }

        @Override
        public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                                               int light, int overlay, int tint, ModelFeatureRenderer.CrumblingOverlay crumbling) {
            this.delegate.submitCrumblingOverlay(model, state, poseStack, renderType, light, overlay, tint, crumbling);
        }

        @Override
        public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState blockState, int light) {
            this.delegate.submitMovingBlock(poseStack, blockState, light);
        }

        @Override
        public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts,
                                     int[] tints, int light, int overlay, int seed) {
            this.delegate.submitBlockModel(poseStack, renderType, parts, tints, light, overlay, seed);
        }

        @Override
        public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean flip) {
            this.delegate.submitBreakingBlockModel(poseStack, parts, progress, flip);
        }

        @Override
        public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float expand, boolean depthTest) {
            this.delegate.submitShapeOutline(poseStack, shape, renderType, color, expand, depthTest);
        }

        @Override
        public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int light, int overlay, int seed,
                               int[] tints, ItemQuads quads, ItemStackRenderState.FoilType foil) {
            this.delegate.submitItem(poseStack, displayContext, light, overlay, seed, tints, quads, foil);
        }

        @Override
        public void submitQuadParticleGroup(QuadParticleRenderState particles) {
            this.delegate.submitQuadParticleGroup(particles);
        }

        @Override
        public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean depthTest) {
            this.delegate.submitGizmoPrimitives(group, camera, depthTest);
        }
    }

    /** Top-level foil wrapper: ordered submissions get an ordered foil delegate. */
    private static final class FoilSubmitNodeCollector extends FoilDelegate implements SubmitNodeCollector {
        private FoilSubmitNodeCollector(SubmitNodeCollector delegate) {
            super(delegate);
        }

        @Override
        public OrderedSubmitNodeCollector order(int order) {
            return new FoilOrderedSubmitNodeCollector(((SubmitNodeCollector) this.delegate).order(order));
        }
    }

    private static final class FoilOrderedSubmitNodeCollector extends FoilDelegate {
        private FoilOrderedSubmitNodeCollector(OrderedSubmitNodeCollector delegate) {
            super(delegate);
        }
    }
}
