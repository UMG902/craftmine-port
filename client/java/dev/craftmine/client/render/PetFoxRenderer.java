package dev.craftmine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.animal.fox.AdultFoxModel;
import net.minecraft.client.model.animal.fox.BabyFoxModel;
import net.minecraft.client.model.animal.fox.FoxModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.FoxHeldItemLayer;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.fox.Fox;
import dev.craftmine.entity.pet.PetFox;

/**
 * Renderer for the pet fox: red/snow textures with separate sleep variants,
 * plus the pounce/faceplant body rotation. The vanilla held-item layer is
 * reused so a pet fox keeps its item-snouting visual.
 */
public class PetFoxRenderer extends AgeableMobRenderer<PetFox, FoxRenderState, FoxModel> {

    private static final Identifier RED_FOX_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox.png");
    private static final Identifier RED_FOX_SLEEP_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox_sleep.png");
    private static final Identifier SNOW_FOX_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox_snow.png");
    private static final Identifier SNOW_FOX_SLEEP_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox_snow_sleep.png");

    public PetFoxRenderer(EntityRendererProvider.Context context) {
        super(context,
            new AdultFoxModel(context.bakeLayer(ModelLayers.FOX)),
            new BabyFoxModel(context.bakeLayer(ModelLayers.FOX_BABY)),
            0.4F);
        this.addLayer(new FoxHeldItemLayer(this));
    }

    /** Pouncing foxes tilt the whole body along their pitch. */
    @Override
    protected void setupRotations(FoxRenderState state, PoseStack poseStack, float bodyRot, float partialTick) {
        super.setupRotations(state, poseStack, bodyRot, partialTick);
        if (state.isPouncing || state.isFaceplanted) {
            poseStack.rotateDegrees(Axis.XP, -state.xRot);
        }
    }

    @Override
    public Identifier getTextureLocation(FoxRenderState state) {
        boolean snow = state.variant != Fox.Variant.RED;
        if (snow) {
            return state.isSleeping ? SNOW_FOX_SLEEP_TEXTURE : SNOW_FOX_TEXTURE;
        }
        return state.isSleeping ? RED_FOX_SLEEP_TEXTURE : RED_FOX_TEXTURE;
    }

    @Override
    public FoxRenderState createRenderState() {
        return new FoxRenderState();
    }

    @Override
    public void extractRenderState(PetFox fox, FoxRenderState state, float partialTick) {
        super.extractRenderState(fox, state, partialTick);
        HoldingEntityRenderState.extractHoldingEntityRenderState(fox, state, this.itemModelResolver);
        state.isCrouching = fox.isCrouching();
        state.isSleeping = fox.isSleeping();
        state.variant = fox.getVariant();
    }
}
