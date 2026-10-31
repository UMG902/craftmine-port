package dev.craftmine.client.render;

import java.util.EnumMap;
import java.util.Map;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.cow.CowVariant;
import dev.craftmine.entity.pet.PetCow;

/**
 * Renderer for the pet cow: normal/warm/cold model families selected by the
 * synced variant, exactly the vanilla cow's three-climate setup.
 */
public class PetCowRenderer extends MobRenderer<PetCow, CowRenderState, CowModel> {

    private final Map<CowVariant.ModelType, AdultAndBabyModelPair<CowModel>> modelsByType;

    public PetCowRenderer(EntityRendererProvider.Context context) {
        super(context, new CowModel(context.bakeLayer(ModelLayers.COW)), 0.7F);
        this.modelsByType = bakeModels(context);
    }

    private static Map<CowVariant.ModelType, AdultAndBabyModelPair<CowModel>> bakeModels(EntityRendererProvider.Context context) {
        Map<CowVariant.ModelType, AdultAndBabyModelPair<CowModel>> models = new EnumMap<>(CowVariant.ModelType.class);
        models.put(CowVariant.ModelType.NORMAL, new AdultAndBabyModelPair<>(
            new CowModel(context.bakeLayer(ModelLayers.COW)),
            new CowModel(context.bakeLayer(ModelLayers.COW_BABY))));
        models.put(CowVariant.ModelType.WARM, new AdultAndBabyModelPair<>(
            new CowModel(context.bakeLayer(ModelLayers.WARM_COW)),
            new CowModel(context.bakeLayer(ModelLayers.WARM_COW_BABY))));
        models.put(CowVariant.ModelType.COLD, new AdultAndBabyModelPair<>(
            new CowModel(context.bakeLayer(ModelLayers.COLD_COW)),
            new CowModel(context.bakeLayer(ModelLayers.COLD_COW_BABY))));
        return models;
    }

    @Override
    public Identifier getTextureLocation(CowRenderState state) {
        return state.variant == null
            ? MissingTextureAtlasSprite.getLocation()
            : state.variant.modelAndTexture().asset().texturePath();
    }

    @Override
    public CowRenderState createRenderState() {
        return new CowRenderState();
    }

    @Override
    public void extractRenderState(PetCow cow, CowRenderState state, float partialTick) {
        super.extractRenderState(cow, state, partialTick);
        state.variant = cow.getVariant().value();
    }

    @Override
    public void submit(CowRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.variant == null) {
            return;
        }
        this.model = this.modelsByType.get(state.variant.modelAndTexture().model()).getModel(state.isBaby);
        super.submit(state, poseStack, collector, camera);
    }
}
