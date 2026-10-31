package dev.craftmine.client.render;

import net.minecraft.client.model.animal.armadillo.AdultArmadilloModel;
import net.minecraft.client.model.animal.armadillo.ArmadilloModel;
import net.minecraft.client.model.animal.armadillo.BabyArmadilloModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArmadilloRenderState;
import net.minecraft.resources.Identifier;
import dev.craftmine.entity.pet.PetArmadillo;

/**
 * Renderer for the pet armadillo: single vanilla texture, with the ball-up
 * state machine's three animation states copied over per frame and the
 * shell-hiding flag taken from the pet's in-state tick counter.
 */
public class PetArmadilloRenderer extends AgeableMobRenderer<PetArmadillo, ArmadilloRenderState, ArmadilloModel> {

    private static final Identifier ARMADILLO_TEXTURE = Identifier.withDefaultNamespace("textures/entity/armadillo/armadillo.png");

    public PetArmadilloRenderer(EntityRendererProvider.Context context) {
        super(context,
            new AdultArmadilloModel(context.bakeLayer(ModelLayers.ARMADILLO)),
            new BabyArmadilloModel(context.bakeLayer(ModelLayers.ARMADILLO_BABY)),
            0.4F);
    }

    @Override
    public Identifier getTextureLocation(ArmadilloRenderState state) {
        return ARMADILLO_TEXTURE;
    }

    @Override
    public ArmadilloRenderState createRenderState() {
        return new ArmadilloRenderState();
    }

    @Override
    public void extractRenderState(PetArmadillo armadillo, ArmadilloRenderState state, float partialTick) {
        super.extractRenderState(armadillo, state, partialTick);
        state.isHidingInShell = armadillo.shouldHideInShell();
        state.peekAnimationState.copyFrom(armadillo.peekAnimationState);
        state.rollOutAnimationState.copyFrom(armadillo.rollOutAnimationState);
        state.rollUpAnimationState.copyFrom(armadillo.rollUpAnimationState);
    }
}
