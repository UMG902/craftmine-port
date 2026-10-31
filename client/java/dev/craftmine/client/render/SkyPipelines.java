package dev.craftmine.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Pipelines for the custom sky contents (Dimension Control picks) that replace
 * vanilla's sky rendering inside the sky render pass:
 *
 * <ul>
 *   <li>{@link #SKY_CUBE} - the data-driven cube sky (CubeSky: any texture
 *       tiled on the six cube faces around the camera; the hub's classic
 *       bedrock box, "Missing", "Bad Idea" (the whole block atlas) and "True
 *       Sky" all render through it). Position/tex/color, translucent blend,
 *       camera-glued: it uses whatever the model-view stack holds during the
 *       sky pass, which in 26.3 does not include camera rotation - the sky
 *       stays fixed on screen while the world moves behind it.</li>
 *   <li>{@link #SKY_CODE} - the "Dev Mode" matrix-rain sky: four textured
 *       walls plus the procedural core/rain fragment shader (falling glyph
 *       runners driven by GameTime, sampling code.png's glyph sheet).</li>
 *   <li>{@link #SKY_BLACK} - the "nothing" sky (task 27): an opaque black
 *       box at far depth, depth-tested so already-drawn terrain stays
 *       visible (26.3 runs the sky pass AFTER opaque terrain - unlike the
 *       1.21.5 fork, where the sky pass could safely clear the target).</li>
 * </ul>
 *
 * <p>All shaders except the rain fragment are the VANILLA core/position_tex
 * and core/position_tex_color pairs - late-resolved mod shader sources proved
 * unreliable in the on-demand pipeline compile, so only core/rain (which is
 * proven to load) stays mod-side, written self-contained to satisfy the
 * 26.3 Vulkan-mode GLSL validator.  Overworld/end skies stay fully vanilla.</p>
 */
public final class SkyPipelines {

        public static final RenderPipeline SKY_CUBE;
        public static final RenderPipeline SKY_CODE;
        public static final RenderPipeline SKY_PANORAMA;
        public static final RenderPipeline SKY_BLACK;

        static {
                SKY_CUBE = RenderPipelines.register(
                        RenderPipeline.builder()
                                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                                .withLocation(Identifier.withDefaultNamespace("pipeline/sky_cube"))
                                .withVertexShader("core/position_tex_color")
                                .withFragmentShader("core/position_tex_color")
                                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                                .build());

                SKY_CODE = RenderPipelines.register(
                        RenderPipeline.builder()
                                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                                // The rain shader reads GameTime from the Globals UBO;
                                // the frontend reverse-validates every shader-defined
                                // uniform against the pipeline's bind group layouts,
                                // so the GLOBALS layout must be declared explicitly.
                                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                                .withLocation(Identifier.withDefaultNamespace("pipeline/sky_code"))
                                .withVertexShader("core/position_tex")
                                .withFragmentShader("core/rain")
                                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                                .build());

                // 26.3's vanilla PANORAMA pipeline switched to a samplerCube
                // design, so the skynorama panorama (six 2D textures on quad
                // faces, like the snapshot) gets its own flat pipeline.
                SKY_PANORAMA = RenderPipelines.register(
                        RenderPipeline.builder()
                                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                                .withLocation(Identifier.withDefaultNamespace("pipeline/sky_panorama"))
                                .withVertexShader("core/position_tex")
                                .withFragmentShader("core/position_tex")
                                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                                .build());

                // Task 27 "nothing" sky: opaque black. NO DepthStencilState on
                // purpose: the 26.3 sky pass runs BEFORE terrain (background
                // semantics), and a LEQUAL/no-write state could never pass the
                // 0.0 (reversed-Z far) depth clear anyway - that is why the
                // box never painted (user screenshot 2). Terrain, drawn later,
                // simply covers the black box.
                SKY_BLACK = RenderPipelines.register(
                        RenderPipeline.builder()
                                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                                .withLocation(Identifier.withDefaultNamespace("pipeline/sky_black"))
                                .withVertexShader("core/position_tex_color")
                                .withFragmentShader("core/position_tex_color")
                                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                                .build());
        }

        private SkyPipelines() {
        }
}
