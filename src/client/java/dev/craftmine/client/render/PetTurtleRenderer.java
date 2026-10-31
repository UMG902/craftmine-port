package dev.craftmine.client.render;

import net.minecraft.client.model.animal.turtle.AdultTurtleModel;
import net.minecraft.client.model.animal.turtle.TurtleModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.TurtleRenderState;
import net.minecraft.resources.Identifier;
import dev.craftmine.entity.pet.PetTurtle;

/**
 * Renderer for the pet turtle: single vanilla texture; the land flag flips
 * the model between its swim and shamble poses.
 */
public class PetTurtleRenderer extends MobRenderer<PetTurtle, TurtleRenderState, TurtleModel> {

    private static final Identifier TURTLE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/turtle/turtle.png");

    public PetTurtleRenderer(EntityRendererProvider.Context context) {
        super(context, new AdultTurtleModel(context.bakeLayer(ModelLayers.TURTLE)), 0.7F);
    }

    @Override
    public TurtleRenderState createRenderState() {
        return new TurtleRenderState();
    }

    @Override
    public void extractRenderState(PetTurtle turtle, TurtleRenderState state, float partialTick) {
        super.extractRenderState(turtle, state, partialTick);
        state.isOnLand = !turtle.isInWater() && turtle.onGround();
    }

    @Override
    public Identifier getTextureLocation(TurtleRenderState state) {
        return TURTLE_TEXTURE;
    }
}
