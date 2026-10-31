package dev.craftmine.client.render;

import net.minecraft.client.model.animal.polarbear.PolarBearModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.PolarBearRenderState;
import net.minecraft.resources.Identifier;
import dev.craftmine.entity.pet.PetPolarBear;

/**
 * Renderer for the pet polar bear: single vanilla texture with the rear-up
 * stand scale fed through (the model rears on its hind legs before it
 * strikes).
 */
public class PetPolarBearRenderer extends AgeableMobRenderer<PetPolarBear, PolarBearRenderState, PolarBearModel> {

    private static final Identifier POLAR_BEAR_TEXTURE = Identifier.withDefaultNamespace("textures/entity/bear/polarbear.png");

    public PetPolarBearRenderer(EntityRendererProvider.Context context) {
        super(context,
            new PolarBearModel(context.bakeLayer(ModelLayers.POLAR_BEAR)),
            new PolarBearModel(context.bakeLayer(ModelLayers.POLAR_BEAR_BABY)),
            0.9F);
    }

    @Override
    public Identifier getTextureLocation(PolarBearRenderState state) {
        return POLAR_BEAR_TEXTURE;
    }

    @Override
    public PolarBearRenderState createRenderState() {
        return new PolarBearRenderState();
    }

    @Override
    public void extractRenderState(PetPolarBear bear, PolarBearRenderState state, float partialTick) {
        super.extractRenderState(bear, state, partialTick);
        state.standScale = bear.getStandingAnimationScale(partialTick);
    }
}
