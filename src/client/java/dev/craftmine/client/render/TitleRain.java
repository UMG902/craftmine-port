package dev.craftmine.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import java.util.Optional;
import java.util.OptionalDouble;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The menu code rain ("Dev Mode" look of the 25w14craftmine snapshot):
 * a single fullscreen glyph wall drawn where 26.3's panorama would go -
 * on the title screen (sharp, user-approved since batch Z) AND behind every
 * other out-of-world screen (Select World, Create World, Options, Connect,
 * ...), where 26.3's own blur stratum + the snapshot pack's dark
 * {@code menu_background.png} veil turn it into the snapshot's dim
 * soft-glyph backdrop. Animated by the ported procedural fragment shader
 * core/title_rain.
 *
 * <p>The draw sequence mirrors {@code CubeMap.render(float, float)} - the
 * vanilla call this replaces (GuiRendererMixin redirects it on every
 * panorama screen) - step for step, and that mirroring is LOAD-BEARING:
 *
 * <ol>
 * <li>{@code Projection.setupPerspective} + {@code RenderSystem.setProjectionMatrix}
 * run BEFORE the render pass opens. The vanilla cubemap path does this every
 * frame; a title-rain draw that relies on the "current" projection instead
 * runs with whatever the previous frame left behind - null on the very first
 * title frame, which made renderpearl's GlCommandEncoder dereference a null
 * GpuBufferSlice ("bufferView is null") and crash the render thread
 * (user crash report 2026-10-29 12.19.42, Intel UHD 730 / GL 3.3). Software
 * rasterizers hid the same bug behind async pipeline compilation.</li>
 * <li>The wall geometry is a frustum cross-section, not a legacy NDC quad:
 * the projection is 26.3's own perspective (zero-to-one reversed-Z clip
 * volume), so clip-space {@literal +/-1} quad corners cover only part of the
 * screen - the classic "rain only in the upper half" bug - while a wall sized
 * from the frustum (depth * tan(fov/2) per axis, aspect-corrected) covers it
 * exactly. UV orientation verified against the snapshot's own quad: texCoord0.y
 * is 0 at the screen bottom, so the runner heads sink and the rain falls.</li>
 * <li>Textures and the compiled pipeline are resolved BEFORE the pass opens
 * (GPU uploads are illegal inside an open pass - same rule the sky renderer
 * follows).</li>
 * <li>{@code bindDefaultUniforms} runs right after {@code setPipeline}, like
 * the vanilla path, so the Globals UBO (GameTime drives the rain motion,
 * ScreenSize the 16px cell grid) and every other implicit block is bound.</li>
 * <li>{@code withCull(false)}: renderpearl's builder defaults to cull=true
 * (cull.orElse(TRUE) in RenderPipeline.Builder.build), and the wall's winding
 * is back-facing through the rotationX(PI) model-view - without this the
 * whole quad is culled and the title renders black.</li>
 * <li>Unlike the vanilla cubemap pass, the render pass CLEARS to opaque
 * black ({@link #CLEAR_BLACK}) every frame: the rain is translucent, so an
 * uncleaned main target would show through - normally black, but the
 * join-world/mine-entry transition frames still hold the loading screen's
 * gray, flashing gray exactly where the snapshot never does.</li>
 * </ol>
 *
 * <p>Failure policy: any throwable inside the draw permanently disables the
 * rain for this session and falls back to the vanilla panorama render - the
 * feature must never take the game down with it.
 */
public final class TitleRain {
    private static final Logger LOGGER = LoggerFactory.getLogger("craftmine/title");

    public static final RenderPipeline TITLE_RAIN = RenderPipelines.register(
            RenderPipeline.builder()
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    // The rain shader reads GameTime + ScreenSize from the Globals
                    // UBO; the frontend reverse-validates shader uniforms against the
                    // pipeline's bind group layouts, so GLOBALS must be declared.
                    .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                    .withLocation(Identifier.withDefaultNamespace("pipeline/title_rain"))
                    .withVertexShader("core/position_tex")
                    .withFragmentShader("core/title_rain")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                    // Builder default is cull.orElse(TRUE) - and the rain wall's
                    // winding is back-facing through the rotationX(PI) model-view,
                    // so without this the whole quad is culled (black title).
                    .withCull(false)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .build());

    /** The snapshot's glyph sheet (served by the craftmine/snapshot asset pack). */
    private static final Identifier CODE_SHEET = Identifier.withDefaultNamespace("textures/font/code.png");
    /** Vanilla panorama perspective parameters (CubeMap.render's constants). */
    private static final float Z_NEAR = 0.05f;
    private static final float Z_FAR = 10.0f;
    private static final float FOV_DEGREES = 85.0f;
    /** View-space depth of the rain wall (safely inside near/far). */
    private static final float WALL_DEPTH = 1.0f;

    /**
     * Opaque black (RGBA) - the render pass's clear color. The rain is blended
     * with {@link BlendFunction#TRANSLUCENT} and the glyph sheet
     * (textures/font/code.png) is fully transparent on ~91% of its pixels, so
     * an UNCLEANED main render target shows through wherever the glyph is
     * transparent. Normally that leftover is black (steady-state menus leave
     * black behind), but the join-world and mine-entry transition frames still
     * hold the loading screen's gray - a one-frame gray flash exactly where the
     * snapshot never shows gray. Clearing every rain frame to opaque black
     * gives the pass the same starting point the steady-state title leaves
     * behind. Vanilla's own cubemap pass can skip the clear
     * (Optional.empty()) because the panorama is fully opaque; the rain is
     * not. 26.3 API note: createRenderPass takes Optional&lt;Vector4fc&gt;
     * (RGBA floats), NOT Optional&lt;Integer&gt; ARGB.
     */
    private static final Vector4fc CLEAR_BLACK = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);

    private static Projection projection;
    private static ProjectionMatrixBuffer projectionUbo;
    private static GlobalSettingsUniform globalSettingsUniform;
    private static GpuBuffer vertexBuffer;
    private static int builtForWidth;
    private static int builtForHeight;
    private static boolean failed;

    private TitleRain() {
    }

    /** False once a draw threw; the redirect then uses the vanilla panorama. */
    public static boolean isUsable() {
        return !failed;
    }

    /**
     * Draws the rain in place of the given cube map. Only called from the
     * GuiRendererMixin redirect when the current screen registered a panorama
     * (title or any out-of-world menu screen) and the snapshot asset pack is
     * active.
     */
    public static void draw(net.minecraft.client.renderer.CubeMap fallback, float pitch, float spin) {
        if (failed) {
            fallback.render(pitch, spin);
            return;
        }
        try {
            drawInner();
        } catch (Throwable t) {
            failed = true;
            LOGGER.error("Craftmine: title rain draw failed - falling back to the vanilla panorama for this session", t);
            try {
                fallback.render(pitch, spin);
            } catch (Throwable suppressed) {
                LOGGER.error("Craftmine: vanilla panorama fallback failed too", suppressed);
            }
        }
    }

    private static void drawInner() {
        Minecraft minecraft = Minecraft.getInstance();
        WindowRenderState window = minecraft.gameRenderer.gameRenderState().windowRenderState;

        // Resolve texture + compiled pipeline BEFORE opening the pass (first
        // touches upload GPU data / trigger shader compilation, which is
        // illegal inside an open render pass).
        AbstractTexture codeSheet = minecraft.getTextureManager().getTexture(CODE_SHEET);
        RenderSystem.getCompiledPipeline(TITLE_RAIN);

        // Mirror CubeMap.render: a REAL perspective projection every frame.
        // Never rely on the ambient projection slice (null on the first title
        // frame - the renderpearl NPE).
        if (projection == null) {
            projection = new Projection();
            projectionUbo = new ProjectionMatrixBuffer("craftmine_title_rain");
        }
        projection.setupPerspective(Z_NEAR, Z_FAR, FOV_DEGREES, (float) window.width, (float) window.height);
        RenderSystem.setProjectionMatrix(projectionUbo.getBuffer(projection), ProjectionType.PERSPECTIVE);

        // GameTime: vanilla's own per-frame update (GameRenderer.render) passes
        // ticks=0/partial=0 on menus (shouldRenderLevel ternaries), so the
        // Globals GameTime the rain shader animates by would stay frozen at 0 -
        // the rain renders a static periodic lattice (user report: "no
        // animation, lower part stitched to fill the bottom"). The snapshot
        // overrode this per-frame with setShaderGameTime(ticks + 1000, partial);
        // mirror that by refreshing the whole Globals block with real option
        // values and our own ticks. update() ends with
        // RenderSystem.setGlobalSettingsUniform(buffer), so the pass below
        // (and the rest of this frame's gui draws) bind the refreshed data.
        if (globalSettingsUniform == null) {
            globalSettingsUniform = new GlobalSettingsUniform();
        }
        var optionsRenderState = minecraft.gameRenderer.gameRenderState().optionsRenderState;
        globalSettingsUniform.update(
                window.width,
                window.height,
                optionsRenderState.glintStrength,
                Util.getMillis() / 50L + 1000L,
                0.0f,
                optionsRenderState.menuBackgroundBlurriness,
                Vec3.ZERO,
                optionsRenderState.textureFiltering == TextureFilteringMethod.RGSS);

        ensureWallBuffer(window.width, window.height);

        // The rain is screen-glued: a plain 180-degree X flip like the vanilla
        // panorama base rotation, WITHOUT the spin/pitch terms (the rain must
        // not rotate with the panorama animation). rotationX overwrites the
        // whole stack, so leftover model-view state cannot leak in.
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.rotationX((float) Math.PI);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelViewStack));
        modelViewStack.popMatrix();

        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indices = indexBuffer.getBuffer(6);
        var target = minecraft.gameRenderer.mainRenderTarget();
        GpuTextureView colorTextureView = target.getColorTextureView();
        GpuTextureView depthTextureView = target.getDepthTextureView();
        // Opaque-black clear every frame (CLEAR_BLACK): Optional.empty() composited
        // the rain over the previous screen's last frame, which flashed gray during
        // the join-world/mine-entry transitions. Vanilla's cubemap pass skips the
        // clear only because the panorama is opaque.
        RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Craftmine title rain", colorTextureView, Optional.of(CLEAR_BLACK), depthTextureView, OptionalDouble.empty());
        try {
            renderPass.pushDebugGroup(() -> "Craftmine title rain");
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(TITLE_RAIN));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setVertexBuffer(0, vertexBuffer.slice());
            renderPass.setIndexBuffer(indices, indexBuffer.type());
            renderPass.setUniform("DynamicTransforms", transform);
            renderPass.setUniform("Sampler0", codeSheet.getTextureView(), codeSheet.getSampler());
            renderPass.drawIndexed(6, 1, 0, 0, 0);
            renderPass.popDebugGroup();
        } finally {
            renderPass.close();
        }
    }

    /**
     * The fullscreen wall: a frustum cross-section at {@link #WALL_DEPTH}:
     * half-height = depth * tan(fov/2), half-width = half-height * aspect.
     * Rebuilt when the window size (aspect) changes.
     */
    private static void ensureWallBuffer(int width, int height) {
        if (vertexBuffer != null && builtForWidth == width && builtForHeight == height) {
            return;
        }
        float yHalf = WALL_DEPTH * (float) Math.tan(Math.toRadians(FOV_DEGREES) * 0.5);
        float xHalf = yHalf * (float) width / (float) height;
        GpuBuffer rebuilt = buildWallBuffer(xHalf, yHalf);
        if (vertexBuffer != null) {
            vertexBuffer.close();
        }
        vertexBuffer = rebuilt;
        builtForWidth = width;
        builtForHeight = height;
    }

    private static GpuBuffer buildWallBuffer(float xHalf, float yHalf) {
        var format = DefaultVertexFormat.POSITION_TEX;
        ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(4 * format.getVertexSize());
        try {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, format);
            // Wall in the model plane z = +WALL_DEPTH; the rotationX(PI)
            // model-view flips it to view depth -WALL_DEPTH (in front of the
            // camera) and flips Y with it. The snapshot's own quad (gwu.d,
            // verbatim) pairs position y=+1 with texCoord0.y=0, which through
            // the same flip lands uv 0 at the screen BOTTOM - reproduced here
            // so the runner heads sink and the rain falls exactly like the
            // original title.
            bufferBuilder.addVertex(-xHalf, yHalf, WALL_DEPTH).setUv(0.0f, 0.0f);
            bufferBuilder.addVertex(-xHalf, -yHalf, WALL_DEPTH).setUv(0.0f, 1.0f);
            bufferBuilder.addVertex(xHalf, -yHalf, WALL_DEPTH).setUv(1.0f, 1.0f);
            bufferBuilder.addVertex(xHalf, yHalf, WALL_DEPTH).setUv(1.0f, 0.0f);
            MeshData meshData = bufferBuilder.buildOrThrow();
            try {
                return RenderSystem.getDevice().createBuffer(() -> "Craftmine title rain wall", 32, meshData.vertexBuffer());
            } finally {
                meshData.close();
            }
        } finally {
            byteBufferBuilder.close();
        }
    }
}
