package dev.craftmine.client.render;

import java.util.EnumMap;
import java.util.Map;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.animal.chicken.AdultChickenModel;
import net.minecraft.client.model.animal.chicken.BabyChickenModel;
import net.minecraft.client.model.animal.chicken.ChickenModel;
import net.minecraft.client.model.animal.chicken.ColdChickenModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import dev.craftmine.entity.pet.PetChicken;

/**
 * Renderer for the pet chicken: picks the normal/cold model family from the
 * synced variant and feeds the flap animation through. Shares all model
 * layers with the vanilla chicken.
 */
public class PetChickenRenderer extends MobRenderer<PetChicken, ChickenRenderState, ChickenModel> {

    private final Map<ChickenVariant.ModelType, AdultAndBabyModelPair<ChickenModel>> modelsByType;

    public PetChickenRenderer(EntityRendererProvider.Context context) {
        super(context, new AdultChickenModel(context.bakeLayer(ModelLayers.CHICKEN)), 0.3F);
        this.modelsByType = bakeModels(context);
    }

    private static Map<ChickenVariant.ModelType, AdultAndBabyModelPair<ChickenModel>> bakeModels(EntityRendererProvider.Context context) {
        Map<ChickenVariant.ModelType, AdultAndBabyModelPair<ChickenModel>> models = new EnumMap<>(ChickenVariant.ModelType.class);
        models.put(ChickenVariant.ModelType.NORMAL, new AdultAndBabyModelPair<>(
            new AdultChickenModel(context.bakeLayer(ModelLayers.CHICKEN)),
            new BabyChickenModel(context.bakeLayer(ModelLayers.CHICKEN_BABY))));
        models.put(ChickenVariant.ModelType.COLD, new AdultAndBabyModelPair<>(
            new ColdChickenModel(context.bakeLayer(ModelLayers.COLD_CHICKEN)),
            new BabyChickenModel(context.bakeLayer(ModelLayers.CHICKEN_BABY))));
        return models;
    }

    @Override
    public void submit(ChickenRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.variant == null) {
            return;
        }
        this.model = this.modelsByType.get(state.variant.modelAndTexture().model()).getModel(state.isBaby);
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(ChickenRenderState state) {
        return state.variant == null
            ? MissingTextureAtlasSprite.getLocation()
            : state.variant.modelAndTexture().asset().texturePath();
    }

    @Override
    public ChickenRenderState createRenderState() {
        return new ChickenRenderState();
    }

    @Override
    public void extractRenderState(PetChicken chicken, ChickenRenderState state, float partialTick) {
        super.extractRenderState(chicken, state, partialTick);
        state.flap = Mth.lerp(partialTick, chicken.oFlap, chicken.flap);
        state.flapSpeed = Mth.lerp(partialTick, chicken.oFlapSpeed, chicken.flapSpeed);
        state.variant = chicken.getVariant().value();
    }
}
