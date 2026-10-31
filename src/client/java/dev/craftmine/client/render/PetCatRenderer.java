package dev.craftmine.client.render;

import net.minecraft.client.model.animal.feline.AbstractFelineModel;
import net.minecraft.client.model.animal.feline.AdultCatModel;
import net.minecraft.client.model.animal.feline.BabyCatModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.CatCollarLayer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.resources.Identifier;
import dev.craftmine.entity.pet.PetCat;

/**
 * Renderer for the pet cat: per-variant textures (baby variants included),
 * plus the collar layer only while tamed — which for pets is always.
 */
public class PetCatRenderer extends AgeableMobRenderer<PetCat, CatRenderState, AbstractFelineModel<CatRenderState>> {

    public PetCatRenderer(EntityRendererProvider.Context context) {
        super(context,
            new AdultCatModel(context.bakeLayer(ModelLayers.CAT)),
            new BabyCatModel(context.bakeLayer(ModelLayers.CAT_BABY)),
            0.4F);
        this.addLayer(new CatCollarLayer(this, context.getModelSet()));
    }

    @Override
    public Identifier getTextureLocation(CatRenderState state) {
        return state.texture;
    }

    @Override
    public CatRenderState createRenderState() {
        return new CatRenderState();
    }

    @Override
    public void extractRenderState(PetCat cat, CatRenderState state, float partialTick) {
        super.extractRenderState(cat, state, partialTick);
        state.texture = cat.getVariant().value().assetInfo(state.isBaby).texturePath();
        state.collarColor = cat.isTame() ? cat.getCollarColor() : null;
    }
}
