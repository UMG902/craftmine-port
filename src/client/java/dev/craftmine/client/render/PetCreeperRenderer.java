package dev.craftmine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CreeperPowerLayer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.resources.Identifier;
import dev.craftmine.entity.pet.PetCreeper;

/**
 * Renderer for the pet creeper: the vanilla creeper model at half scale,
 * with the charged-power layer (harmless — a pet creeper never builds a
 * charge, but the layer keeps the swelled-gradient visuals consistent).
 */
public class PetCreeperRenderer extends MobRenderer<PetCreeper, CreeperRenderState, CreeperModel> {

    private static final Identifier CREEPER_TEXTURE = Identifier.withDefaultNamespace("textures/entity/creeper/creeper.png");
    private static final float PET_SCALE = 0.5F;

    public PetCreeperRenderer(EntityRendererProvider.Context context) {
        super(context, new CreeperModel(context.bakeLayer(ModelLayers.CREEPER)), 0.2F);
        this.addLayer(new CreeperPowerLayer(this, context.getModelSet()));
    }

    @Override
    protected void scale(CreeperRenderState state, PoseStack poseStack) {
        poseStack.scale(PET_SCALE, PET_SCALE, PET_SCALE);
    }

    @Override
    public Identifier getTextureLocation(CreeperRenderState state) {
        return CREEPER_TEXTURE;
    }

    @Override
    public CreeperRenderState createRenderState() {
        return new CreeperRenderState();
    }
}
