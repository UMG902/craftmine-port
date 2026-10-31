package dev.craftmine.client.render;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/**
 * A prepared sky-block draw list: the vertex/index slices of every visible
 * section's {@code SKY_BLOCK} mesh, keyed by layer, plus the shared dynamic
 * transform buffer carrying the per-draw glint matrix.
 *
 * <p>{@link #draw} opens one render pass over the main level target (the same
 * target that is bound as Sampler1 - each fragment reads exactly the pixel
 * behind it before writing, which works on Vulkan/GL as in the original), binds
 * the block atlas (Sampler0), the live frame (Sampler1) and the enchanted glint
 * texture (Sampler2), then runs the layer's draws with the scrolling glint
 * matrix.</p>
 *
 * <p>Mirrors the original snapshot's draw (recovered from the Exotelcraft
 * reference implementation for 26.3).</p>
 */
public record SkyBlockDrawList(
        GpuTextureView atlasTextureView,
        EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> drawsPerLayer,
        int maxIndicesRequired,
        GpuBufferSlice[] dynamicTransforms) {

        public static final Identifier GLINT_TEXTURE = ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR;

        public boolean isEmpty() {
                for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : this.drawsPerLayer.values()) {
                        if (!draws.isEmpty()) {
                                return false;
                        }
                }
                return true;
        }

        /**
         * Runs the prepared draws for the given group. No-op unless the group is
         * the runtime-added {@code SKY_BLOCK} group.
         */
        public void draw(ChunkSectionLayerGroup group) {
                if (group != ChunkSectionLayerGroup.valueOf("SKY_BLOCK")) {
                        return;
                }
                if (this.isEmpty()) {
                        return;
                }
                RenderSystem.AutoStorageIndexBuffer sequentialIndexBuffer = RenderSystem
                        .getSequentialBuffer(PrimitiveTopology.QUADS);
                GpuBuffer indexBuffer = this.maxIndicesRequired == 0 ? null : sequentialIndexBuffer.getBuffer(this.maxIndicesRequired);
                IndexType indexType = this.maxIndicesRequired == 0 ? null : sequentialIndexBuffer.type();
                ChunkSectionLayer[] layers = group.layers();
                Minecraft minecraft = Minecraft.getInstance();
                RenderTarget skyTarget = ((dev.craftmine.duck.SkyBlockLevelRendererDuck) minecraft.levelRenderer)
                        .craftmine$getSkyTarget();
                AbstractTexture atlasTexture = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
                AbstractTexture glintTexture = minecraft.getTextureManager().getTexture(GLINT_TEXTURE);
                GpuTextureView skyTextureView = skyTarget.getColorTextureView();
                try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Section layers for " + group.label(), skyTextureView, Optional.empty(),
                        skyTarget.getDepthTextureView(), OptionalDouble.empty())) {
                        RenderSystem.bindDefaultUniforms(renderPass);
                        for (ChunkSectionLayer layer : layers) {
                                List<RenderPass.Draw<GpuBufferSlice[]>> draws = this.drawsPerLayer.get(layer);
                                if (draws == null || draws.isEmpty()) {
                                        continue;
                                }
                                RenderPipeline pipeline = layer.pipeline(false);
                                renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
                                renderPass.setUniform("Sampler0", atlasTexture.getTextureView(), atlasTexture.getSampler());
                                renderPass.setUniform("Sampler1", skyTextureView,
                                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                                renderPass.setUniform("Sampler2", glintTexture.getTextureView(), glintTexture.getSampler());
                                renderPass.drawMultipleIndexed(draws, indexBuffer, indexType,
                                        List.of("DynamicTransforms"), this.dynamicTransforms);
                        }
                }
        }

        /**
         * The scrolling enchanted-glint texture matrix (two different scroll
         * periods so the pattern takes long to repeat, rotated 10 degrees, scaled
         * to 1% - identical to the original snapshot's {@code setupGlintTexturing}).
         */
        public static org.joml.Matrix4f setupGlintTexturing(float scale) {
                long time = (long) ((double) net.minecraft.util.Util.getMillis()
                        * (Double) Minecraft.getInstance().options.glintSpeed().get() * 8.0);
                float scrollX = (float) (time % 110000L) / 110000.0f;
                float scrollY = (float) (time % 30000L) / 30000.0f;
                org.joml.Matrix4f matrix = new org.joml.Matrix4f().translation(-scrollX, scrollY, 0.0f);
                matrix.rotateZ(0.17453292f).scale(scale);
                return matrix;
        }
}
