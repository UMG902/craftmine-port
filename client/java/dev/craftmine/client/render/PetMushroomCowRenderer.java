package dev.craftmine.client.render;

import java.util.Map;
import net.minecraft.client.model.animal.cow.BabyCowModel;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.MushroomCowMushroomLayer;
import net.minecraft.client.renderer.entity.state.MushroomCowRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import dev.craftmine.entity.pet.PetMushroomCow;

/**
 * Renderer for the pet mooshroom: red/brown textures plus the vanilla
 * mushroom-back layer, whose block model is resolved once per frame from
 * the current variant.
 */
public class PetMushroomCowRenderer extends AgeableMobRenderer<PetMushroomCow, MushroomCowRenderState, CowModel> {

    /** Block-render context used to bake the back mushroom (static display context is fine on the client thread). */
    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

    private static final Map<MushroomCow.Variant, Identifier> TEXTURES = Util.make(new java.util.HashMap<MushroomCow.Variant, Identifier>(), map -> {
        map.put(MushroomCow.Variant.BROWN, Identifier.withDefaultNamespace("textures/entity/cow/mooshroom_brown.png"));
        map.put(MushroomCow.Variant.RED, Identifier.withDefaultNamespace("textures/entity/cow/mooshroom_red.png"));
    });

    private final BlockModelResolver blockModelResolver;

    public PetMushroomCowRenderer(EntityRendererProvider.Context context) {
        super(context,
            new CowModel(context.bakeLayer(ModelLayers.MOOSHROOM)),
            new BabyCowModel(context.bakeLayer(ModelLayers.MOOSHROOM_BABY)),
            0.7F);
        this.blockModelResolver = context.getBlockModelResolver();
        this.addLayer(new MushroomCowMushroomLayer(this));
    }

    @Override
    public Identifier getTextureLocation(MushroomCowRenderState state) {
        return TEXTURES.get(state.variant);
    }

    @Override
    public MushroomCowRenderState createRenderState() {
        return new MushroomCowRenderState();
    }

    @Override
    public void extractRenderState(PetMushroomCow cow, MushroomCowRenderState state, float partialTick) {
        super.extractRenderState(cow, state, partialTick);
        state.variant = cow.getVariant();
        this.blockModelResolver.update(state.mushroomModel, state.variant.getBlockState(), BLOCK_DISPLAY_CONTEXT);
    }
}
