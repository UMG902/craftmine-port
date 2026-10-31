package dev.craftmine.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.resources.Identifier;

/**
 * Pipelines for the Craftmine SkyWindows system (the 26.3 re-implementation
 * of the original 25w14craftmine snapshot's sky_block pass - see
 * {@link SkyWindowRenderer} for the architecture).
 *
 * <p>Both pipelines here are registered from mod code because the snapshot
 * built its equivalents natively:</p>
 *
 * <ul>
 *   <li>{@link #SKY_BLOCK} - {@code minecraft:pipeline/sky_block}: the window
 *       surface shader. An inner snippet built on
 *       {@link RenderPipelines#MATRICES_FOG_SNIPPET} (Projection +
 *       DynamicTransforms UBOs + fog), the snapshot's core/sky_block shader
 *       pair (Mojang's own assets, shipped unmodified by the snapshot jar and
 *       adapted to the 26.3 GLSL layout), the SAMPLER0_SAMPLER1_SAMPLER2 bind
 *       group (block atlas / finished-frame copy / enchanted glint), the
 *       BLOCK vertex format, quad topology and the default depth-stencil
 *       state (reversed-Z ties pass, so coplanar window faces deterministically
 *       win over the wall faces behind them). Opaque color target.</li>
 *   <li>{@link #SKY_WINDOW_COPY} - {@code minecraft:pipeline/sky_window_copy}:
 *       a plain fullscreen color blit (vanilla core/screenquad +
 *       core/blit_screen, the same pair vanilla's own blit pipelines use)
 *       that snapshots the main color target into the aux copy before the
 *       window pass samples it. This copy is what makes the window sampling
 *       feedback-loop-free on every backend.</li>
 * </ul>
 *
 * <p>26.3 keeps both {@code RenderPipelines.MATRICES_FOG_SNIPPET} and
 * {@code RenderPipelines.register} private - the port's access widener opens
 * them (same mechanism as the other widened internals).</p>
 */
public final class SkyBlockPipelines {

        public static final Identifier GLINT_TEXTURE = ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR;

        /**
         * The opaque sky-block (window surface) pipeline.
         */
        public static final RenderPipeline SKY_BLOCK;

        /**
         * Fullscreen color blit: main color target -> aux copy.
         */
        public static final RenderPipeline SKY_WINDOW_COPY;

        static {
                RenderPipeline.Snippet snippet = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                        .withVertexShader("core/sky_block")
                        .withFragmentShader("core/sky_block")
                        .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
                        .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                        .withPrimitiveTopology(PrimitiveTopology.QUADS)
                        .withDepthStencilState(DepthStencilState.DEFAULT)
                        .buildSnippet();
                SKY_BLOCK = RenderPipelines.register(
                        RenderPipeline.builder(snippet)
                                .withLocation(Identifier.withDefaultNamespace("pipeline/sky_block"))
                                .withColorTargetState(ColorTargetState.DEFAULT)
                                .build());

                SKY_WINDOW_COPY = RenderPipelines.register(
                        RenderPipeline.builder()
                                .withLocation(Identifier.withDefaultNamespace("pipeline/sky_window_copy"))
                                .withVertexShader("core/screenquad")
                                .withFragmentShader("core/blit_screen")
                                .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                                .withColorTargetState(ColorTargetState.DEFAULT)
                                .build());
        }

        private SkyBlockPipelines() {
        }
}
