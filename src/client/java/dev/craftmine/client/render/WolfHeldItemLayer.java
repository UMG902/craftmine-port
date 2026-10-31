package dev.craftmine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.WeakHashMap;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Draws the item a pet wolf holds in its mouth.
 *
 * <p>26.3 note: {@link WolfRenderState} no longer extends
 * HoldingEntityRenderState, so there is no held-item slot on the render
 * state. Render states are freshly extracted every frame
 * ({@code EntityRenderer.createRenderState}), so the prepared
 * {@link ItemStackRenderState} is keyed per state here with weak keys
 * (entries die with the frame's state).</p>
 */
public class WolfHeldItemLayer extends RenderLayer<WolfRenderState, WolfModel> {

    /** Head-pivot rotations shared by both baby and adult poses. */
    private static final float MOUTH_YAW_DEGREES = 30.0F;
    private static final float MOUTH_PITCH_DEGREES = 90.0F;

    private final ItemModelResolver itemModelResolver;
    private final WeakHashMap<WolfRenderState, ItemStackRenderState> heldItems = new WeakHashMap<>();

    public WolfHeldItemLayer(RenderLayerParent<WolfRenderState, WolfModel> parent, ItemModelResolver itemModelResolver) {
        super(parent);
        this.itemModelResolver = itemModelResolver;
    }

    /** Called during state extraction, right before the layer's submit pass. */
    public void extractHeldItem(WolfRenderState state, LivingEntity wolf) {
        ItemStackRenderState itemState = new ItemStackRenderState();
        this.itemModelResolver.updateForLiving(itemState, wolf.getMainHandItem(), ItemDisplayContext.GROUND, wolf);
        this.heldItems.put(state, itemState);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, WolfRenderState state, float yaw, float pitch) {
        ItemStackRenderState itemState = this.heldItems.get(state);
        if (itemState == null || itemState.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        ModelPart head = this.getParentModel().root().getChild("head");
        poseStack.translate(head.x / 16.0F, head.y / 16.0F, head.z / 16.0F);
        if (state.isBaby) {
            poseStack.scale(0.75F, 0.75F, 0.75F);
        }
        poseStack.rotate(Axis.ZP, state.headRollAngle);
        poseStack.rotateDegrees(Axis.YP, yaw);
        poseStack.rotateDegrees(Axis.XP, pitch);
        if (state.isBaby) {
            poseStack.translate(0.27F, 0.19F, -0.45F);
        } else {
            poseStack.translate(0.22F, 0.12F, -0.35F);
        }
        poseStack.rotateDegrees(Axis.YP, MOUTH_YAW_DEGREES);
        poseStack.rotateDegrees(Axis.XP, MOUTH_PITCH_DEGREES);
        itemState.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
