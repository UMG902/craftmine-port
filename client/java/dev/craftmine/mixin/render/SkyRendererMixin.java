/*
 * Renders the Dimension Control sky picks in place of vanilla's sky: the
 * cube skies (Hub classic / Missing / Bad Idea / True Sky), the "Smol"
 * skynorama panorama and the "Dev Mode" code rain.  Overworld and End skies
 * stay vanilla.
 *
 * Ported from the snapshot's own renderer (gwu/gvl decompiles): the custom sky
 * is drawn into the SkyRenderer's render target (= main) inside the vanilla
 * "sky" frame pass. The model-view at sky time DOES contain the camera
 * rotation (26.3's LevelRenderer.render pushes cameraState.viewRotationMatrix
 * onto the model-view stack before the frame graph runs), so the cube skies
 * are world-fixed and rotate with the view exactly like the original. The
 * panorama gets the snapshot's post-multiplied 180-degree X flip on top
 * (Matrix4fStack.rotateX, NOT rotationX - rotationX OVERWRITES the stack and
 * would glue the panorama to the screen: task 47's "smol" bug).
 */
package dev.craftmine.mixin.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.resources.Identifier;
import dev.craftmine.level.DimensionSpecialEffects;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.craftmine.client.render.SkyPipelines;
import dev.craftmine.duck.CraftmineClientLevelDuck;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

    static {
        // Force SkyPipelines' <clinit> at BOOT (mixin apply time), before the
        // resource reload snapshots the pipeline list.  Late-registered
        // pipelines compile on demand through the fallback pipeline cache,
        // whose ShaderSource provider was built from the boot-time resource
        // manager (vanilla pack only) - mod shaders like core/sky_flat would
        // then fail to resolve ("Couldn't preload shader").  SkyBlockPipelines
        // already relies on the same effect via ChunkSectionLayerMixin.
        // (Reading a static field triggers the <clinit>; getClass() would not.)
        if (SkyPipelines.SKY_CUBE == null || SkyPipelines.SKY_CODE == null || SkyPipelines.SKY_PANORAMA == null || SkyPipelines.SKY_BLACK == null) {
            throw new IllegalStateException("SkyPipelines failed to initialize");
        }
    }

    @Shadow
    @Final
    private RenderTarget renderTarget;

    @Unique
    private static final Identifier craftmine$PANORAMA_BASE = Identifier.withDefaultNamespace("textures/environment/skynorama/panorama");

    @Unique
    private static final List<Identifier> PANORAMA_SIDES = List.of(
        craftmine$PANORAMA_BASE.withSuffix("_0.png"),
        craftmine$PANORAMA_BASE.withSuffix("_1.png"),
        craftmine$PANORAMA_BASE.withSuffix("_2.png"),
        craftmine$PANORAMA_BASE.withSuffix("_3.png"),
        craftmine$PANORAMA_BASE.withSuffix("_4.png"),
        craftmine$PANORAMA_BASE.withSuffix("_5.png")
    );

    @Unique
    private final Map<Long, GpuBuffer> craftmine$cubeSkyCache = new HashMap<>();

    @Unique
    private GpuBuffer craftmine$panoramaBuffer;

    @Unique
    private GpuBuffer craftmine$codeSkyBuffer;

    @Unique
    private GpuBuffer craftmine$blackSkyBuffer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$init(TextureManager textureManager, AtlasManager atlasManager, RenderTarget renderTarget, CallbackInfo ci) {
        this.craftmine$panoramaBuffer = craftmine$buildPanoramaBuffer();
        this.craftmine$codeSkyBuffer = craftmine$buildCodeSky();
        // Size 90: every face sits inside the WORST-case projection far plane
        // (Camera.depthFar = max(renderDistance*4, cloudRange*16) - about 128
        // at the minimum render distance), so the box can never clip. The old
        // size-400 box was BEYOND the far plane at the user's render distance
        // - the whole "nothing" sky silently vanished and the blue fog clear
        // showed instead (user screenshot 2).
        this.craftmine$blackSkyBuffer = craftmine$buildCube(DefaultVertexFormat.POSITION_TEX_COLOR, 90.0f, 1, 0xFF000000);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void craftmine$close(CallbackInfo ci) {
        for (GpuBuffer buffer : this.craftmine$cubeSkyCache.values()) {
            buffer.close();
        }
        this.craftmine$cubeSkyCache.clear();
        if (this.craftmine$panoramaBuffer != null) {
            this.craftmine$panoramaBuffer.close();
            this.craftmine$panoramaBuffer = null;
        }
        if (this.craftmine$codeSkyBuffer != null) {
            this.craftmine$codeSkyBuffer.close();
            this.craftmine$codeSkyBuffer = null;
        }
        if (this.craftmine$blackSkyBuffer != null) {
            this.craftmine$blackSkyBuffer.close();
            this.craftmine$blackSkyBuffer = null;
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void craftmine$renderCustomSky(GpuBufferSlice fogSlice, SkyRenderState skyRenderState, CallbackInfo ci) {
        if (Boolean.getBoolean("craftmine.vanillaSky")) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (!(level instanceof CraftmineClientLevelDuck duck)) {
            return;
        }
        DimensionSpecialEffects.Sky sky = duck.craftmine$getActiveSky();
        // Overworld / End (and no-sky) stay fully vanilla.
        if (sky == null
            || sky instanceof DimensionSpecialEffects.OverworldSky
            || sky instanceof DimensionSpecialEffects.EndSky) {
            return;
        }
        ci.cancel();

        // Task 27 - the "nothing" sky: the fork's sky pass skips ALL sky
        // content (no disc, no celestial bodies, no sunrise) when its sky
        // optional is empty. 26.3 runs the sky pass BEFORE opaque terrain
        // (clear pass -> sky pass -> main pass, confirmed in the 26.3
        // LevelRenderer decompile AND empirically: terrain draws over the
        // code-rain walls in the user's screenshots), so the black box is a
        // plain background paint with NO depth state (see SkyPipelines
        // SKY_BLACK) - the old LEQUAL/no-write state could never pass the
        // 0.0 (reversed-Z far) depth clear, which is the other reason the
        // box never showed. Terrain, drawn later, simply covers it.
        if (sky instanceof DimensionSpecialEffects.NothingSky) {
            // 1x1 opaque white mod texture; the cube's opaque BLACK vertex
            // color multiplies it to solid black (alpha 1 keeps the blend
            // fully covering).
            AbstractTexture whiteTexture = Minecraft.getInstance().getTextureManager()
                .getTexture(Identifier.withDefaultNamespace("textures/misc/craftmine_white.png"));
            com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline compiledBlack =
                RenderSystem.getCompiledPipeline(SkyPipelines.SKY_BLACK);

            RenderSystem.setShaderFog(fogSlice);
            GpuTextureView colorTextureView = this.renderTarget.getColorTextureView();
            GpuTextureView depthTextureView = this.renderTarget.getDepthTextureView();
            RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Craftmine black sky", colorTextureView, Optional.empty(), depthTextureView, OptionalDouble.empty());
            RenderSystem.bindDefaultUniforms(renderPass);
            try {
                RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                GpuBuffer indices = indexBuffer.getBuffer(36);
                GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
                renderPass.pushDebugGroup(() -> "Craftmine black sky");
                renderPass.setPipeline(compiledBlack);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", transform);
                renderPass.setUniform("Sampler0", whiteTexture.getTextureView(), whiteTexture.getSampler());
                renderPass.setVertexBuffer(0, this.craftmine$blackSkyBuffer.slice());
                renderPass.setIndexBuffer(indices, indexBuffer.type());
                renderPass.drawIndexed(36, 1, 0, 0, 0);
                renderPass.popDebugGroup();
            } finally {
                renderPass.close();
            }
            return;
        }

        // Resolve ALL textures BEFORE opening the render pass: first-time loads
        // upload GPU data, which is illegal inside an open render pass (the
        // reference implementation preloads its textures in <init> for the
        // same reason).
        AbstractTexture skyTexture = null;
        List<AbstractTexture> panoramaTextures = List.of();
        if (sky instanceof DimensionSpecialEffects.CubeSky cubeSky) {
            Identifier textureLocation = cubeSky.textureId().withPath(path -> "textures/" + path + ".png");
            // Task 52 - "Bad Idea" (textureId atlas/blocks): that id collides
            // with the TextureManager registration path of the LIVE stitched
            // block atlas, so this sky rendered 26.3's runtime atlas - a
            // different sprite set and layout than the snapshot's ("miles
            // worse"). The snapshot's atlas was never shipped (stitching is
            // runtime-only, exists solely in VRAM), so no resource trick can
            // reproduce it (atlas jsons only ADD sources; 26.3's texture dirs
            // hold ~300 more sprites than the snapshot's). Fix: bind the
            // F3+S-dumped snapshot sheet instead - the exact 1024x512 image
            // (1900 sprites) the snapshot's own client had stitched, captured
            // from the real 25w14craftmine client. Animated sprites (water,
            // lava, fire, portal) are frozen mid-frame by definition of a
            // static dump; the sprite-position txt cannot restore animation
            // (it stores coordinates only). All other cube skies unaffected.
            if (textureLocation.equals(Identifier.withDefaultNamespace("textures/atlas/blocks.png"))) {
                textureLocation = Identifier.withDefaultNamespace("textures/environment/atlas_sky.png");
            }
            skyTexture = Minecraft.getInstance().getTextureManager().getTexture(textureLocation);
        } else if (sky instanceof DimensionSpecialEffects.CodeSky) {
            skyTexture = Minecraft.getInstance().getTextureManager().getTexture(Identifier.withDefaultNamespace("textures/font/code.png"));
        } else if (sky instanceof DimensionSpecialEffects.Panorama) {
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            panoramaTextures = PANORAMA_SIDES.stream()
                .map(textureManager::getTexture)
                .toList();
        }

        // Resolve the pipelines BEFORE opening the pass too (first use triggers
        // an async compile - never do that while a render pass is open).
        com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline compiledCube = null;
        com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline compiledCode = null;
        com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline compiledPanorama = null;
        if (sky instanceof DimensionSpecialEffects.CubeSky) {
            compiledCube = RenderSystem.getCompiledPipeline(SkyPipelines.SKY_CUBE);
        } else if (sky instanceof DimensionSpecialEffects.CodeSky) {
            compiledCode = RenderSystem.getCompiledPipeline(SkyPipelines.SKY_CODE);
        } else if (sky instanceof DimensionSpecialEffects.Panorama) {
            compiledPanorama = RenderSystem.getCompiledPipeline(SkyPipelines.SKY_PANORAMA);
        }

        RenderSystem.setShaderFog(fogSlice);
        GpuTextureView colorTextureView = this.renderTarget.getColorTextureView();
        GpuTextureView depthTextureView = this.renderTarget.getDepthTextureView();
        RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass(() -> "Craftmine sky", colorTextureView, Optional.empty(), depthTextureView, OptionalDouble.empty());
        RenderSystem.bindDefaultUniforms(renderPass);
        try {
            if (sky instanceof DimensionSpecialEffects.CubeSky cubeSky) {
                craftmine$renderCubeSky(renderPass, cubeSky, skyTexture, compiledCube);
            } else if (sky instanceof DimensionSpecialEffects.CodeSky) {
                craftmine$renderCodeSky(renderPass, skyTexture, compiledCode);
            } else if (sky instanceof DimensionSpecialEffects.Panorama) {
                craftmine$renderPanorama(renderPass, panoramaTextures, compiledPanorama);
            }
        } finally {
            renderPass.close();
        }
    }

    @Unique
    private void craftmine$renderCubeSky(RenderPass renderPass, DimensionSpecialEffects.CubeSky cubeSky, AbstractTexture texture, com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline compiled) {
        GpuBuffer vertexBuffer = craftmine$cubeBuffer(cubeSky.repeats(), cubeSky.size());
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indices = indexBuffer.getBuffer(36);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
        renderPass.pushDebugGroup(() -> "Craftmine cube sky");
        renderPass.setPipeline(compiled);
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform("DynamicTransforms", transform);
        renderPass.setUniform("Sampler0", texture.getTextureView(), texture.getSampler());
        renderPass.setVertexBuffer(0, vertexBuffer.slice());
        renderPass.setIndexBuffer(indices, indexBuffer.type());
        renderPass.drawIndexed(36, 1, 0, 0, 0);
        renderPass.popDebugGroup();
    }

    @Unique
    private void craftmine$renderCodeSky(RenderPass renderPass, AbstractTexture codeTexture, com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline compiled) {
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indices = indexBuffer.getBuffer(24);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
        renderPass.pushDebugGroup(() -> "Craftmine code sky");
        renderPass.setPipeline(compiled);
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform("DynamicTransforms", transform);
        renderPass.setUniform("Sampler0", codeTexture.getTextureView(), codeTexture.getSampler());
        renderPass.setVertexBuffer(0, this.craftmine$codeSkyBuffer.slice());
        renderPass.setIndexBuffer(indices, indexBuffer.type());
        renderPass.drawIndexed(24, 1, 0, 0, 0);
        renderPass.popDebugGroup();
    }

    /**
     * Task 47: the snapshot's in-world panorama (gwu.c) post-multiplies a
     * 180-degree X rotation onto the sky model-view (which already carries the
     * camera rotation): {@code modelViewStack.rotateX(PI)}. The old port used
     * {@code rotationX(PI)} - JOML's rotationX REPLACES the whole matrix, so
     * the camera rotation was destroyed and the panorama was glued to the
     * screen ("always facing the same exact face and point"). rotateX
     * post-multiplies: same neutral-view image as before, but it now rotates
     * with the view like the original.
     */
    @Unique
    private void craftmine$renderPanorama(RenderPass renderPass, List<AbstractTexture> panoramaTextures, com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline compiled) {
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indices = indexBuffer.getBuffer(36);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.rotateX((float) Math.PI);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
        renderPass.pushDebugGroup(() -> "Craftmine panorama sky");
        renderPass.setPipeline(compiled);
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform("DynamicTransforms", transform);
        renderPass.setVertexBuffer(0, this.craftmine$panoramaBuffer.slice());
        renderPass.setIndexBuffer(indices, indexBuffer.type());
        for (int side = 0; side < 6; side++) {
            AbstractTexture sideTexture = panoramaTextures.get(side);
            renderPass.setUniform("Sampler0", sideTexture.getTextureView(), sideTexture.getSampler());
            renderPass.drawIndexed(6, 1, side * 6, 0, 0);
        }
        renderPass.popDebugGroup();
        modelViewStack.popMatrix();
    }

    @Unique
    private GpuBuffer craftmine$cubeBuffer(int repeats, float size) {
        long key = ((long) repeats << 32) | (Float.floatToRawIntBits(size) & 0xFFFFFFFFL);
        return this.craftmine$cubeSkyCache.computeIfAbsent(key,
            ignored -> craftmine$buildCube(DefaultVertexFormat.POSITION_TEX_COLOR, size, repeats, 0xFFFFFFFF));
    }

    /**
     * Builds the six-face camera cube for the CubeSky skies and the nothing
     * sky: one face definition (at the -Y plane, half extent {@code size},
     * UVs 0..{@code repeats}) swept through the six face rotations - the same
     * construction the snapshot's gwu cube builder and 26.3's own end-sky
     * buffer use. The cube carries the given ARGB vertex color (white for the
     * cube skies, opaque black for the nothing sky). Faces are wound INWARD
     * (visible from inside), matching both references.
     */
    @Unique
    private static GpuBuffer craftmine$buildCube(VertexFormat format, float size, int repeats, int vertexColor) {
        boolean withColor = format == DefaultVertexFormat.POSITION_TEX_COLOR;
        int vertexCount = 6 * 4;
        ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(vertexCount * format.getVertexSize());
        try {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, format);
            Vector3f transformed = new Vector3f();
            List<Matrix4f> rotations = craftmine$faceRotations();
            for (int face = 0; face < 6; face++) {
                Matrix4f rotation = rotations.get(face);
                transformed.set(-size, -size, -size).mulPosition(rotation);
                bufferBuilder.addVertex(transformed.x, transformed.y, transformed.z).setUv(0.0f, 0.0f);
                if (withColor) {
                    bufferBuilder.setColor(vertexColor);
                }
                transformed.set(-size, -size, size).mulPosition(rotation);
                bufferBuilder.addVertex(transformed.x, transformed.y, transformed.z).setUv(0.0f, (float) repeats);
                if (withColor) {
                    bufferBuilder.setColor(vertexColor);
                }
                transformed.set(size, -size, size).mulPosition(rotation);
                bufferBuilder.addVertex(transformed.x, transformed.y, transformed.z).setUv((float) repeats, (float) repeats);
                if (withColor) {
                    bufferBuilder.setColor(vertexColor);
                }
                transformed.set(size, -size, -size).mulPosition(rotation);
                bufferBuilder.addVertex(transformed.x, transformed.y, transformed.z).setUv((float) repeats, 0.0f);
                if (withColor) {
                    bufferBuilder.setColor(vertexColor);
                }
            }
            MeshData meshData = bufferBuilder.buildOrThrow();
            GpuBuffer buffer;
            try {
                buffer = RenderSystem.getDevice().createBuffer(() -> "Craftmine sky cube", 32, meshData.vertexBuffer());
            } finally {
                meshData.close();
            }
            return buffer;
        } finally {
            byteBufferBuilder.close();
        }
    }

    /**
     * The snapshot's in-world panorama cube (gvl.a's "Cube map vertex
     * buffer"), VERBATIM: half-extent 1, six faces in the order +Z, +X, -Z,
     * -X, bottom (-Y), top (+Y), each with UVs 0..1 and the snapshot's exact
     * corner/UV assignment, so panorama_0..5 land on the same faces as the
     * original (with the render-time rotateX(PI) flip, panorama_0 is the face
     * you see looking forward). The old port swept ONE base face through six
     * rotations in a different order, which put the wrong panorama file on the
     * wrong face.
     */
    @Unique
    private static GpuBuffer craftmine$buildPanoramaBuffer() {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;
        ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(24 * format.getVertexSize());
        try {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, format);
            // +Z
            bufferBuilder.addVertex(-1.0f, -1.0f, 1.0f).setUv(0.0f, 0.0f);
            bufferBuilder.addVertex(-1.0f, 1.0f, 1.0f).setUv(0.0f, 1.0f);
            bufferBuilder.addVertex(1.0f, 1.0f, 1.0f).setUv(1.0f, 1.0f);
            bufferBuilder.addVertex(1.0f, -1.0f, 1.0f).setUv(1.0f, 0.0f);
            // +X
            bufferBuilder.addVertex(1.0f, -1.0f, 1.0f).setUv(0.0f, 0.0f);
            bufferBuilder.addVertex(1.0f, 1.0f, 1.0f).setUv(0.0f, 1.0f);
            bufferBuilder.addVertex(1.0f, 1.0f, -1.0f).setUv(1.0f, 1.0f);
            bufferBuilder.addVertex(1.0f, -1.0f, -1.0f).setUv(1.0f, 0.0f);
            // -Z
            bufferBuilder.addVertex(1.0f, -1.0f, -1.0f).setUv(0.0f, 0.0f);
            bufferBuilder.addVertex(1.0f, 1.0f, -1.0f).setUv(0.0f, 1.0f);
            bufferBuilder.addVertex(-1.0f, 1.0f, -1.0f).setUv(1.0f, 1.0f);
            bufferBuilder.addVertex(-1.0f, -1.0f, -1.0f).setUv(1.0f, 0.0f);
            // -X
            bufferBuilder.addVertex(-1.0f, -1.0f, -1.0f).setUv(0.0f, 0.0f);
            bufferBuilder.addVertex(-1.0f, 1.0f, -1.0f).setUv(0.0f, 1.0f);
            bufferBuilder.addVertex(-1.0f, 1.0f, 1.0f).setUv(1.0f, 1.0f);
            bufferBuilder.addVertex(-1.0f, -1.0f, 1.0f).setUv(1.0f, 0.0f);
            // -Y (bottom)
            bufferBuilder.addVertex(-1.0f, -1.0f, -1.0f).setUv(0.0f, 0.0f);
            bufferBuilder.addVertex(-1.0f, -1.0f, 1.0f).setUv(0.0f, 1.0f);
            bufferBuilder.addVertex(1.0f, -1.0f, 1.0f).setUv(1.0f, 1.0f);
            bufferBuilder.addVertex(1.0f, -1.0f, -1.0f).setUv(1.0f, 0.0f);
            // +Y (top)
            bufferBuilder.addVertex(-1.0f, 1.0f, 1.0f).setUv(0.0f, 0.0f);
            bufferBuilder.addVertex(-1.0f, 1.0f, -1.0f).setUv(0.0f, 1.0f);
            bufferBuilder.addVertex(1.0f, 1.0f, -1.0f).setUv(1.0f, 1.0f);
            bufferBuilder.addVertex(1.0f, 1.0f, 1.0f).setUv(1.0f, 0.0f);
            MeshData meshData = bufferBuilder.buildOrThrow();
            GpuBuffer buffer;
            try {
                buffer = RenderSystem.getDevice().createBuffer(() -> "Craftmine panorama sky cube", 32, meshData.vertexBuffer());
            } finally {
                meshData.close();
            }
            return buffer;
        } finally {
            byteBufferBuilder.close();
        }
    }

    /**
     * The "Dev Mode" walls: four 200-wide, 800-tall walls at x/z = +/-100,
     * textured with code.png (the procedural rain shader animates it).
     */
    @Unique
    private static GpuBuffer craftmine$buildCodeSky() {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;
        ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(16 * format.getVertexSize());
        try {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, format);
            Vector3f transformed = new Vector3f();
            for (int wall = 0; wall < 4; wall++) {
                Matrix4f rotation = new Matrix4f();
                switch (wall) {
                    case 1 -> rotation.rotationY((float) (-Math.PI / 2));
                    case 2 -> rotation.rotationY((float) (Math.PI / 2));
                    case 3 -> rotation.rotationY((float) Math.PI);
                    default -> rotation.identity();
                }
                transformed.set(100.0f, 400.0f, -100.0f).mulPosition(rotation);
                bufferBuilder.addVertex(transformed.x, transformed.y, transformed.z).setUv(1.0f, 1.0f);
                transformed.set(-100.0f, 400.0f, -100.0f).mulPosition(rotation);
                bufferBuilder.addVertex(transformed.x, transformed.y, transformed.z).setUv(0.0f, 1.0f);
                transformed.set(-100.0f, -400.0f, -100.0f).mulPosition(rotation);
                bufferBuilder.addVertex(transformed.x, transformed.y, transformed.z).setUv(0.0f, 0.0f);
                transformed.set(100.0f, -400.0f, -100.0f).mulPosition(rotation);
                bufferBuilder.addVertex(transformed.x, transformed.y, transformed.z).setUv(1.0f, 0.0f);
            }
            MeshData meshData = bufferBuilder.buildOrThrow();
            GpuBuffer buffer;
            try {
                buffer = RenderSystem.getDevice().createBuffer(() -> "Craftmine code sky", 32, meshData.vertexBuffer());
            } finally {
                meshData.close();
            }
            return buffer;
        } finally {
            byteBufferBuilder.close();
        }
    }

    @Unique
    private static List<Matrix4f> craftmine$faceRotations() {
        return List.of(
            new Matrix4f().identity(),
            new Matrix4f().rotationX((float) (Math.PI / 2)),
            new Matrix4f().rotationX((float) (-Math.PI / 2)),
            new Matrix4f().rotationX((float) Math.PI),
            new Matrix4f().rotationZ((float) (Math.PI / 2)),
            new Matrix4f().rotationZ((float) (-Math.PI / 2))
        );
    }
}
