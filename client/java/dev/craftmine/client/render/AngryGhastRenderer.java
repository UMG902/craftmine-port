package dev.craftmine.client.render;

import net.minecraft.client.model.monster.ghast.GhastModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.GhastRenderState;
import net.minecraft.resources.Identifier;
import dev.craftmine.entity.AngryGhast;

/**
 * Renderer for the angry ghast special-mine boss: the vanilla ghast model
 * with its own two-face texture set — the squinting "shooting" face while
 * it is charging a fireball, the grumpy resting face otherwise.
 */
public class AngryGhastRenderer extends MobRenderer<AngryGhast, GhastRenderState, GhastModel> {

    private static final Identifier GHAST_TEXTURE = Identifier.withDefaultNamespace("textures/entity/angry_ghast/ghast.png");
    private static final Identifier GHAST_SHOOTING_TEXTURE = Identifier.withDefaultNamespace("textures/entity/angry_ghast/ghast_shooting.png");

    public AngryGhastRenderer(EntityRendererProvider.Context context) {
        super(context, new GhastModel(context.bakeLayer(ModelLayers.GHAST)), 1.5F);
    }

    @Override
    public Identifier getTextureLocation(GhastRenderState state) {
        return state.isCharging ? GHAST_SHOOTING_TEXTURE : GHAST_TEXTURE;
    }

    @Override
    public GhastRenderState createRenderState() {
        return new GhastRenderState();
    }

    @Override
    public void extractRenderState(AngryGhast ghast, GhastRenderState state, float partialTick) {
        super.extractRenderState(ghast, state, partialTick);
        state.isCharging = ghast.isCharging();
    }
}
