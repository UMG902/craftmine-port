package dev.craftmine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import dev.craftmine.entity.pet.PetSlime;

/**
 * Renderer for the pet slime: vanilla slime squish math (compress on land,
 * stretch in flight) sized by the locked pet size, plus the translucent
 * outer layer.
 */
public class PetSlimeRenderer extends MobRenderer<PetSlime, SlimeRenderState, SlimeModel> {

    private static final Identifier SLIME_TEXTURE = Identifier.withDefaultNamespace("textures/entity/slime/slime.png");
    /** Base shadow radius per unit of slime size. */
    private static final float SHADOW_PER_SIZE = 0.25F;
    /** Tiny inset so the slime model never z-fights with the floor. */
    private static final float FLOOR_INSET = 0.001F;
    private static final float SQUISH_JITTER = 0.999F;

    public PetSlimeRenderer(EntityRendererProvider.Context context) {
        super(context, new SlimeModel(context.bakeLayer(ModelLayers.SLIME)), 0.25F);
        this.addLayer(new SlimeOuterLayer(this, context.getModelSet()));
    }

    @Override
    protected float getShadowRadius(SlimeRenderState state) {
        return state.size * SHADOW_PER_SIZE;
    }

    @Override
    protected void scale(SlimeRenderState state, PoseStack poseStack) {
        poseStack.scale(SQUISH_JITTER, SQUISH_JITTER, SQUISH_JITTER);
        poseStack.translate(0.0F, FLOOR_INSET, 0.0F);
        float size = state.size;
        // squish in [-0.5, 1] normalized against the body volume:
        float squish = state.squish / (size * 0.5F + 1.0F);
        float squash = 1.0F / (squish + 1.0F);
        poseStack.scale(squash * size, 1.0F / squash * size, squash * size);
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        return SLIME_TEXTURE;
    }

    @Override
    public SlimeRenderState createRenderState() {
        return new SlimeRenderState();
    }

    @Override
    public void extractRenderState(PetSlime slime, SlimeRenderState state, float partialTick) {
        super.extractRenderState(slime, state, partialTick);
        state.squish = Mth.lerp(partialTick, slime.oSquish, slime.squish);
        state.size = slime.getSize();
    }
}
