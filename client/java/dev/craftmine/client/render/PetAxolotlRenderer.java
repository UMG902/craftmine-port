package dev.craftmine.client.render;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.axolotl.AdultAxolotlModel;
import net.minecraft.client.model.animal.axolotl.BabyAxolotlModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AxolotlRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import dev.craftmine.entity.pet.PetAxolotl;

/**
 * Renderer for the pet axolotl: one texture per vanilla variant plus the
 * three binary-animator factors (in-water / on-ground / moving) that drive
 * the limb poses.
 */
public class PetAxolotlRenderer extends AgeableMobRenderer<PetAxolotl, AxolotlRenderState, EntityModel<AxolotlRenderState>> {

    private static final Map<Axolotl.Variant, Identifier> TEXTURES = Util.make(new HashMap<>(), map -> {
        for (Axolotl.Variant variant : Axolotl.Variant.values()) {
            map.put(variant, Identifier.withDefaultNamespace(
                String.format(Locale.ROOT, "textures/entity/axolotl/axolotl_%s.png", variant.getName())));
        }
    });

    public PetAxolotlRenderer(EntityRendererProvider.Context context) {
        super(context,
            new AdultAxolotlModel(context.bakeLayer(ModelLayers.AXOLOTL)),
            new BabyAxolotlModel(context.bakeLayer(ModelLayers.AXOLOTL_BABY)),
            0.5F);
    }

    @Override
    public Identifier getTextureLocation(AxolotlRenderState state) {
        return TEXTURES.get(state.variant);
    }

    @Override
    public AxolotlRenderState createRenderState() {
        return new AxolotlRenderState();
    }

    @Override
    public void extractRenderState(PetAxolotl axolotl, AxolotlRenderState state, float partialTick) {
        super.extractRenderState(axolotl, state, partialTick);
        state.variant = axolotl.getVariant();
        state.inWaterFactor = axolotl.inWaterAnimator.getFactor(partialTick);
        state.onGroundFactor = axolotl.onGroundAnimator.getFactor(partialTick);
        state.movingFactor = axolotl.movingAnimator.getFactor(partialTick);
    }
}
