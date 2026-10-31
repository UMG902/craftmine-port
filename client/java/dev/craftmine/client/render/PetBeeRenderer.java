package dev.craftmine.client.render;

import net.minecraft.client.model.animal.bee.AdultBeeModel;
import net.minecraft.client.model.animal.bee.BabyBeeModel;
import net.minecraft.client.model.animal.bee.BeeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.resources.Identifier;
import dev.craftmine.entity.pet.PetBee;

/**
 * Renderer for the pet bee: the plain nectar-free bee texture on the
 * vanilla bee model, adult + baby. (The fork's pet bee never carries
 * nectar and never renders the angry sting face — those texture slots
 * exist in the vanilla bee renderer only.)
 */
public class PetBeeRenderer extends AgeableMobRenderer<PetBee, BeeRenderState, BeeModel> {

    private static final Identifier BEE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/bee/bee.png");

    public PetBeeRenderer(EntityRendererProvider.Context context) {
        super(context,
            new AdultBeeModel(context.bakeLayer(ModelLayers.BEE)),
            new BabyBeeModel(context.bakeLayer(ModelLayers.BEE_BABY)),
            0.4F);
    }

    @Override
    public Identifier getTextureLocation(BeeRenderState state) {
        return BEE_TEXTURE;
    }

    @Override
    public BeeRenderState createRenderState() {
        return new BeeRenderState();
    }
}
