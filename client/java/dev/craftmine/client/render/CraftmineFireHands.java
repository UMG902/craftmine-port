/*
 * FT-8 gameplay layer (client) - YOU_ARE_THE_CAMPFIRE fire hands.
 * Port of the fork's static ItemInHandLayer.renderFireHands(scale, poseStack,
 * multiBufferSource): four fullbright fire quads (FIRE_0/FIRE_1 alternating)
 * billboarded around the hand anchor. 26.3 lost MultiBufferSource-based
 * immediate rendering, so each face group goes through
 * SubmitNodeCollector.submitCustomGeometry with its own pose rotation -
 * the vertex stream (color -1, uv, overlay 0/10, light 240, normal +Y) is
 * byte-for-byte the fork's fireVertex output.
 */
package dev.craftmine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;

public final class CraftmineFireHands {
    private CraftmineFireHands() {
    }

    public static void render(float scale, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        TextureAtlasSprite fire0 = Minecraft.getInstance().getAtlasManager().get(ModelBakery.FIRE_0);
        TextureAtlasSprite fire1 = Minecraft.getInstance().getAtlasManager().get(ModelBakery.FIRE_1);
        var renderType = RenderTypes.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS);
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.rotateDegrees(com.mojang.math.Axis.XP, 90.0F);
        poseStack.rotateDegrees(com.mojang.math.Axis.ZP, 180.0F);
        poseStack.translate(0.0, -0.2, 0.2);
        float half = 0.5F;
        submitFace(submitNodeCollector, renderType, poseStack, fire0, half);
        poseStack.rotateDegrees(com.mojang.math.Axis.YP, 180.0F);
        submitFace(submitNodeCollector, renderType, poseStack, fire1, half);
        poseStack.rotateDegrees(com.mojang.math.Axis.YP, 90.0F);
        submitFace(submitNodeCollector, renderType, poseStack, fire0, half);
        poseStack.rotateDegrees(com.mojang.math.Axis.YP, 180.0F);
        submitFace(submitNodeCollector, renderType, poseStack, fire1, half);
        poseStack.popPose();
    }

    private static void submitFace(SubmitNodeCollector submitNodeCollector, net.minecraft.client.renderer.rendertype.RenderType renderType,
                                   PoseStack poseStack, TextureAtlasSprite sprite, float half) {
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
            fireVertex(pose, vertexConsumer, -half, 0.0F, 0.0F, u0, v1);
            fireVertex(pose, vertexConsumer, half, 0.0F, 0.0F, u1, v1);
            fireVertex(pose, vertexConsumer, half, 1.4F, 0.0F, u1, v0);
            fireVertex(pose, vertexConsumer, -half, 1.4F, 0.0F, u0, v0);
        });
    }

    private static void fireVertex(PoseStack.Pose pose, VertexConsumer vertexConsumer, float x, float y, float z, float u, float v) {
        vertexConsumer.addVertex(pose, x, y, z)
            .setColor(-1)
            .setUv(u, v)
            .setUv1(0, 10)
            .setLight(240)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
