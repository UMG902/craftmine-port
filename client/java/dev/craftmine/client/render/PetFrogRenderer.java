package dev.craftmine.client.render;

import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.FrogRenderState;
import net.minecraft.resources.Identifier;
import dev.craftmine.entity.pet.PetFrog;

/**
 * Renderer for the pet frog: per-variant texture plus all four animation
 * states (jump / croak / tongue / swim-idle) copied from the entity each
 * frame.
 */
public class PetFrogRenderer extends MobRenderer<PetFrog, FrogRenderState, FrogModel> {

    public PetFrogRenderer(EntityRendererProvider.Context context) {
        super(context, new FrogModel(context.bakeLayer(ModelLayers.FROG)), 0.3F);
    }

    @Override
    public Identifier getTextureLocation(FrogRenderState state) {
        return state.texture;
    }

    @Override
    public FrogRenderState createRenderState() {
        return new FrogRenderState();
    }

    @Override
    public void extractRenderState(PetFrog frog, FrogRenderState state, float partialTick) {
        super.extractRenderState(frog, state, partialTick);
        state.isSwimming = frog.isInWater();
        state.jumpAnimationState.copyFrom(frog.jumpAnimationState);
        state.croakAnimationState.copyFrom(frog.croakAnimationState);
        state.tongueAnimationState.copyFrom(frog.tongueAnimationState);
        state.swimIdleAnimationState.copyFrom(frog.swimIdleAnimationState);
        state.texture = frog.getVariant().value().assetInfo().texturePath();
    }
}
