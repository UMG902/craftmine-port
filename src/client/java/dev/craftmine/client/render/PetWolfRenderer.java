package dev.craftmine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.animal.wolf.BabyWolfModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import dev.craftmine.entity.pet.PetWolf;

/**
 * Renderer for the pet wolf: reuses the vanilla wolf model pipeline (armor +
 * collar layers) and feeds it the pet's head-tilt/shake/wetness animation
 * state. The held-item bridge exists because {@link WolfRenderState} no
 * longer carries an item slot in 26.3 — see {@link WolfHeldItemLayer}.
 */
public class PetWolfRenderer extends AgeableMobRenderer<PetWolf, WolfRenderState, WolfModel> {

    private final WolfHeldItemLayer heldItemLayer;

    public PetWolfRenderer(EntityRendererProvider.Context context) {
        super(context,
            new AdultWolfModel(context.bakeLayer(ModelLayers.WOLF)),
            new BabyWolfModel(context.bakeLayer(ModelLayers.WOLF_BABY)),
            0.5F);
        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new WolfCollarLayer(this));
        this.heldItemLayer = new WolfHeldItemLayer(this, this.itemModelResolver);
        this.addLayer(this.heldItemLayer);
    }

    /** Wet fur darkens the whole model; while shaking it eases back to untinted. */
    @Override
    protected int getModelTint(WolfRenderState state) {
        float wetShade = state.wetShade;
        if (wetShade == 1.0F) {
            return -1; // no tint
        }
        return ARGB.colorFromFloat(1.0F, wetShade, wetShade, wetShade);
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.texture;
    }

    @Override
    public WolfRenderState createRenderState() {
        return new WolfRenderState();
    }

    @Override
    public void extractRenderState(PetWolf wolf, WolfRenderState state, float partialTick) {
        super.extractRenderState(wolf, state, partialTick);
        this.heldItemLayer.extractHeldItem(state, wolf);
        state.isAngry = wolf.isAngry();
        state.isSitting = wolf.isInSittingPose();
        state.headRollAngle = wolf.getHeadRollAngle(partialTick);
        state.shakeAnim = wolf.getShakeAnim(partialTick);
        state.texture = wolf.getTexture();
        state.wetShade = wolf.getWetShade(partialTick);
        state.collarColor = wolf.isTame() ? wolf.getCollarColor() : null;
        state.bodyArmorItem = wolf.getBodyArmorItem().copy();
    }
}
