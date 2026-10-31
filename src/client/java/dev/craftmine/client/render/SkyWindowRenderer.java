/*
 * Craftmine SkyWindows - the 26.3 implementation of the snapshot's sky_block
 * pass (the "window" surfaces: minecraft:sky, the shimmering door and the
 * dimension control block).
 *
 * Design goals, in order:
 *
 *  1. FIX THE SHIMMERING-DOOR FLICKER. The previous implementation hooked
 *     LevelRenderer.prepareTranslucents, which on 1.21.5 ran "after opaque,
 *     before translucent" - but on 26.3 prepareTranslucents is a PREPARE
 *     method that runs BEFORE the main render pass opens. The windows were
 *     drawn before the opaque scene existed (so they sampled the previous
 *     frame -> temporal feedback/parallax = the flicker) and were then
 *     partially overwritten by coplanar wall faces drawn later under
 *     reversed-Z tie-breaking (the "see-through/z-fighting" look). The same
 *     bug ships in ExotelCraft, whose 26.3 reference implementation the old
 *     port mirrored. THIS implementation is independent of that design.
 *
 *  2. RENDERER-AGNOSTIC (Sodium-safe). The three window blocks return
 *     RenderShape.INVISIBLE, so neither the vanilla nor the Sodium chunk
 *     mesher ever produces geometry for them. All window geometry comes from
 *     THIS class: it tessellates each block with vanilla's own
 *     ModelBlockRenderer (identical AO/tint/face-culling to what the chunk
 *     mesher would emit) into per-chunk vertex buffers it owns.
 *
 *  3. NO FEEDBACK LOOP, SNAPSHOT-FAITHFUL ORDER. In 25w14craftmine the
 *     sky_block surface is a real chunk-mesh RenderType listed FIRST among
 *     the chunk layers - before solid terrain - and its shader samples the
 *     color target as it was at that moment: sky only, no terrain. It writes
 *     depth, so the terrain behind the windows is simply depth-rejected by
 *     the main pass ("nothing renders behind a sky wall"), and the sampled
 *     sky never varies with the scene (temporally stable = no flicker).
 *     On 26.3 the frame graph is clear -> sky -> main; two frame passes are
 *     registered in the gap between sky and main (LevelRendererMixin injects
 *     addMainPass HEAD):
 *       - "craftmine_sky_copy": blit of the target as it is after the sky
 *         pass (sky only, no terrain) into an aux target (TextureTarget, no
 *         depth). The copy exists because the window pass both samples and
 *         writes main - sampling the live target inside its own pass is a
 *         feedback hazard.
 *       - "craftmine_sky_windows": draws the window geometry into main
 *         (color + depth, DepthStencilState.DEFAULT) while sampling the
 *         sky-only AUX copy as Sampler1. The main pass then chains after:
 *         geometry behind the windows is depth-rejected (snapshot occlusion),
 *         geometry in front draws over them.
 *     Drawing the windows AFTER main (the first 26.3 attempt) was wrong: the
 *     copy then contained the finished scene, so the shader's screen-space
 *     mix(scene, glyphs, glint) made every wall standing in front of geometry
 *     show that geometry through itself ("hall visible through the sky
 *     blocks").
 *
 *  The window shader itself (core/sky_block.*) and the pipeline layout are
 *  the original 25w14craftmine snapshot's (Mojang's own assets), ported to
 *  the 26.3 renderpearl API in SkyBlockPipelines.
 * Data flow:
 *   - Chunks in view distance are scanned once per level for the three block
 *     types (lazy, cached per ClientLevel).
 *   - ClientLevelMixin.sendBlockUpdated adds/removes single positions
 *     incrementally; ClientPacketListenerMixin invalidates a chunk when its
 *     full data (re)arrives or is forgotten. Any change re-tessellates that
 *     chunk before the next draw.
 *   - Tessellation: ModelBlockRenderer.tesselateBlock -> BlockQuadOutput ->
 *     BufferBuilder.putBlockBakedQuad (BLOCK vertex format) -> MeshData ->
 *     one GpuBuffer per chunk (GpuBuffer.Usage.VERTEX).
 *   - Draw: one RenderPass.Draw per chunk with its own DynamicTransforms
 *     (ModelOffset = chunkOrigin - camera, scrolling glint matrix), the
 *     shared sequential quad index buffer, pipeline = SkyBlockPipelines.
 *     SKY_BLOCK, Sampler0 = block atlas, Sampler1 = sky-only aux copy,
 *     Sampler2 = enchanted glint.
 */
package dev.craftmine.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import dev.craftmine.registry.CraftmineContent;

public final class SkyWindowRenderer {

        public static final Identifier GLINT_TEXTURE =
                net.minecraft.client.renderer.feature.ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR;

        private static final SkyWindowRenderer INSTANCE = new SkyWindowRenderer();

        public static SkyWindowRenderer get() {
                return INSTANCE;
        }

        private SkyWindowRenderer() {
        }

        /** Per-chunk collected geometry. */
        private static final class ChunkWindows {
                final LongSet positions = new LongOpenHashSet();
                boolean scanned;
                boolean meshDirty;
                GpuBuffer vertexBuffer;
                int indexCount;
        }

        private ClientLevel level;
        private final Long2ObjectOpenHashMap<ChunkWindows> chunks = new Long2ObjectOpenHashMap<>();

        /** Tessellation scratch - render thread only, like all of this class. */
        private ByteBufferBuilder byteBufferBuilder;
        private ModelBlockRenderer modelBlockRenderer;

        /** The sky-only copy (target as of just after the sky pass) the windows sample. */
        private TextureTarget auxTarget;

        // ------------------------------------------------------------------
        // World-driven cache maintenance (called from client mixins, render
        // thread).
        // ------------------------------------------------------------------

        public void onLevelChanged(ClientLevel newLevel) {
                if (this.level == newLevel) {
                        return;
                }
                this.clear();
                this.level = newLevel;
        }

        private void clear() {
                for (ChunkWindows chunk : this.chunks.values()) {
                        this.closeChunk(chunk);
                }
                this.chunks.clear();
        }

        private void closeChunk(ChunkWindows chunk) {
                if (chunk.vertexBuffer != null) {
                        chunk.vertexBuffer.close();
                        chunk.vertexBuffer = null;
                }
                chunk.indexCount = 0;
        }

        /** A single block changed somewhere in the client level. */
        public void onBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState) {
                if (this.level == null) {
                        return;
                }
                boolean oldIsWindow = isWindowBlock(oldState);
                boolean newIsWindow = isWindowBlock(newState);
                if (!oldIsWindow && !newIsWindow) {
                        return;
                }
                long chunkKey = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
                ChunkWindows chunk = this.chunks.get(chunkKey);
                if (chunk == null) {
                        chunk = new ChunkWindows();
                        this.chunks.put(chunkKey, chunk);
                }
                if (oldIsWindow) {
                        chunk.positions.remove(pos.asLong());
                }
                if (newIsWindow) {
                        chunk.positions.add(pos.asLong());
                }
                chunk.meshDirty = true;
        }

        /** A chunk's full data arrived (fresh or replaced) - rescan it. */
        public void onChunkReload(int chunkX, int chunkZ) {
                if (this.level == null) {
                        return;
                }
                ChunkWindows chunk = this.chunks.remove(ChunkPos.pack(chunkX, chunkZ));
                if (chunk != null) {
                        this.closeChunk(chunk);
                }
        }

        private static boolean isWindowBlock(BlockState state) {
                return state.is(CraftmineContent.SKY) || state.is(CraftmineContent.SHIMMERING_DOOR)
                        || state.is(CraftmineContent.DIMENSION_CONTROL);
        }

        // ------------------------------------------------------------------
        // Frame entry point (from LevelRendererMixin, at addMainPass HEAD, so
        // the two passes below execute between the "sky" pass and the "main"
        // pass every frame - the snapshot's sky_block chunk-layer position).
        // ------------------------------------------------------------------

        public void registerPasses(FrameGraphBuilder frame, LevelTargetBundle targets,
                LevelRenderState levelRenderState) {
                if (java.lang.Boolean.getBoolean("craftmine.disableSkyWindows")) {
                        return;
                }
                Minecraft minecraft = Minecraft.getInstance();
                this.onLevelChanged(minecraft.level);
                if (this.level == null) {
                        return;
                }

                this.scanAndTessellate();
                if (!this.hasGeometry()) {
                        return;
                }

                RenderTarget mainRenderTarget = minecraft.gameRenderer.mainRenderTarget();
                TextureTarget aux = this.ensureAuxTarget(mainRenderTarget.width, mainRenderTarget.height);

                ResourceHandle<RenderTarget> auxHandle = frame.importExternal("craftmine_sky_aux", aux);

                FramePass copyPass = frame.addPass("craftmine_sky_copy");
                targets.main = copyPass.readsAndWrites(targets.main);
                ResourceHandle<RenderTarget> auxCopyHandle = copyPass.readsAndWrites(auxHandle);
                copyPass.executes(() -> this.executeCopyPass(targets, auxCopyHandle));

                FramePass windowPass = frame.addPass("craftmine_sky_windows");
                targets.main = windowPass.readsAndWrites(targets.main);
                windowPass.reads(auxCopyHandle);
                windowPass.executes(() -> this.executeWindowPass(targets, auxCopyHandle, levelRenderState));
        }

        private TextureTarget ensureAuxTarget(int width, int height) {
                if (this.auxTarget == null) {
                        this.auxTarget = new TextureTarget("Craftmine Sky Windows Aux", width, height,
                                GpuFormat.RGBA8_UNORM, null);
                } else if (this.auxTarget.width != width || this.auxTarget.height != height) {
                        this.auxTarget.resize(width, height);
                }
                return this.auxTarget;
        }

        private boolean hasGeometry() {
                for (ChunkWindows chunk : this.chunks.values()) {
                        if (chunk.indexCount > 0) {
                                return true;
                        }
                }
                return false;
        }

        // ------------------------------------------------------------------
        // Scan + tessellate.
        // ------------------------------------------------------------------

        private void scanAndTessellate() {
                Minecraft minecraft = Minecraft.getInstance();
                ClientLevel clientLevel = this.level;
                if (minecraft.player == null) {
                        return;
                }
                int viewDistance = minecraft.options.renderDistance().get();
                int centerChunkX = minecraft.player.blockPosition().getX() >> 4;
                int centerChunkZ = minecraft.player.blockPosition().getZ() >> 4;

                // 1) scan chunks that entered the view distance (lazy, once per
                // level entry per chunk)
                for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                        for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                                int chunkX = centerChunkX + dx;
                                int chunkZ = centerChunkZ + dz;
                                long chunkKey = ChunkPos.pack(chunkX, chunkZ);
                                ChunkWindows chunk = this.chunks.get(chunkKey);
                                if (chunk == null) {
                                        chunk = new ChunkWindows();
                                        this.chunks.put(chunkKey, chunk);
                                }
                                if (!chunk.scanned) {
                                        this.scanChunk(clientLevel, chunkX, chunkZ, chunk);
                                        chunk.scanned = true;
                                        chunk.meshDirty = true;
                                }
                        }
                }

                // 2) rebuild dirty meshes
                boolean anyDirty = false;
                for (ChunkWindows chunk : this.chunks.values()) {
                        if (chunk.meshDirty) {
                                anyDirty = true;
                                break;
                        }
                }
                if (anyDirty) {
                        this.ensureTessellationResources();
                        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
                        BlockModelLighter.enableCaching();
                        for (Long2ObjectMap.Entry<ChunkWindows> entry : this.chunks.long2ObjectEntrySet()) {
                                ChunkWindows chunk = entry.getValue();
                                if (chunk.meshDirty) {
                                        this.tessellateChunk(clientLevel, entry.getLongKey(), chunk, modelSet);
                                        chunk.meshDirty = false;
                                }
                        }
                        BlockModelLighter.clearCache();
                }
        }

        private void scanChunk(ClientLevel clientLevel, int chunkX, int chunkZ, ChunkWindows chunk) {
                net.minecraft.world.level.chunk.ChunkAccess chunkAccess = clientLevel.getChunk(chunkX, chunkZ,
                        ChunkStatus.FULL, false);
                if (chunkAccess == null) {
                        return;
                }
                LevelChunkSection[] sections = chunkAccess.getSections();
                int minSectionY = clientLevel.getMinSectionY();
                for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                        LevelChunkSection section = sections[sectionIndex];
                        if (section.hasOnlyAir()) {
                                continue;
                        }
                        int baseY = (minSectionY + sectionIndex) << 4;
                        for (int packedIndex = 0; packedIndex < 4096; packedIndex++) {
                                BlockState state = section.getBlockState(packedIndex & 15, (packedIndex >> 4) & 15,
                                        packedIndex >> 8);
                                if (isWindowBlock(state)) {
                                        int x = (chunkX << 4) + (packedIndex & 15);
                                        int z = (chunkZ << 4) + (packedIndex >> 8);
                                        chunk.positions.add(BlockPos.asLong(x, baseY + ((packedIndex >> 4) & 15), z));
                                }
                        }
                }
        }

        private void ensureTessellationResources() {
                if (this.modelBlockRenderer == null) {
                        Minecraft minecraft = Minecraft.getInstance();
                        boolean ambientOcclusion = minecraft.options.ambientOcclusion().get();
                        this.modelBlockRenderer = new ModelBlockRenderer(ambientOcclusion, true,
                                minecraft.getBlockColors());
                }
                if (this.byteBufferBuilder == null) {
                        this.byteBufferBuilder = new ByteBufferBuilder(1 << 16);
                }
        }

        private void tessellateChunk(ClientLevel clientLevel, long chunkKey, ChunkWindows chunk,
                BlockStateModelSet modelSet) {
                this.closeChunk(chunk);
                if (chunk.positions.isEmpty()) {
                        return;
                }
                int chunkX = ChunkPos.getX(chunkKey);
                int chunkZ = ChunkPos.getZ(chunkKey);
                int minY = clientLevel.getMinY();
                // A BufferBuilder runs one build session; vanilla news a fresh one
                // per compile (SectionCompiler.getOrBeginLayer) - do the same per
                // chunk, over the shared scratch ByteBufferBuilder.
                BufferBuilder builder = new BufferBuilder(this.byteBufferBuilder, PrimitiveTopology.QUADS,
                        DefaultVertexFormat.BLOCK);
                BlockQuadOutput output = (x, y, z, quad, instance) -> builder.putBlockBakedQuad(x, y, z,
                        quad, instance);
                for (long packedPos : chunk.positions) {
                        BlockPos pos = BlockPos.of(packedPos);
                        BlockState state = clientLevel.getBlockState(pos);
                        if (!isWindowBlock(state)) {
                                continue;
                        }
                        this.modelBlockRenderer.tesselateBlock(output, pos.getX() & 15, pos.getY() - minY,
                                pos.getZ() & 15, clientLevel, pos, state, modelSet.get(state), state.getSeed(pos));
                }
                MeshData mesh = builder.build();
                if (mesh != null) {
                        try {
                                GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                                        () -> "Craftmine Sky Window Chunk", GpuBuffer.USAGE_VERTEX,
                                        mesh.vertexBuffer());
                                chunk.vertexBuffer = buffer;
                                chunk.indexCount = mesh.drawState().indexCount();
                        } finally {
                                mesh.close();
                        }
                }
        }

        // ------------------------------------------------------------------
        // Passes.
        // ------------------------------------------------------------------

        private void executeCopyPass(LevelTargetBundle targets, ResourceHandle<RenderTarget> auxHandle) {
                // Runs after the sky pass, before the main pass: main currently
                // holds the sky (clear color / skybox) and NO terrain - exactly
                // what the snapshot's sky_block layer sampled.
                RenderTarget mainTarget = targets.main.get();
                RenderTarget auxTarget = auxHandle.get();
                if (mainTarget == null || auxTarget == null) {
                        return;
                }
                try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Craftmine Sky Window Copy", auxTarget.getColorTextureView(), Optional.empty(), null,
                        OptionalDouble.empty())) {
                        RenderSystem.bindDefaultUniforms(renderPass);
                        renderPass.setPipeline(RenderSystem.getCompiledPipeline(SkyBlockPipelines.SKY_WINDOW_COPY));
                        renderPass.setUniform("InSampler", mainTarget.getColorTextureView(),
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                        renderPass.draw(3, 1, 0, 0);
                }
        }

        private void executeWindowPass(LevelTargetBundle targets, ResourceHandle<RenderTarget> auxHandle,
                LevelRenderState levelRenderState) {
                RenderTarget mainTarget = targets.main.get();
                RenderTarget auxTarget = auxHandle.get();
                if (mainTarget == null || auxTarget == null || this.level == null) {
                        return;
                }

                List<DynamicGpuData.Transform> transforms = new ArrayList<>();
                List<RenderPass.Draw<GpuBufferSlice[]>> draws = new ArrayList<>();
                int maxIndicesRequired = 0;

                if (Minecraft.getInstance().player == null) {
                        return;
                }
                double camX = levelRenderState.cameraRenderState.pos.x;
                double camY = levelRenderState.cameraRenderState.pos.y;
                double camZ = levelRenderState.cameraRenderState.pos.z;
                Matrix4f glintMatrix = setupGlintTexturing(0.01f);
                Vector4f white = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
                int minY = this.level.getMinY();

                for (Long2ObjectMap.Entry<ChunkWindows> entry : this.chunks.long2ObjectEntrySet()) {
                        ChunkWindows chunk = entry.getValue();
                        if (chunk.vertexBuffer == null || chunk.indexCount <= 0) {
                                continue;
                        }
                        int chunkX = ChunkPos.getX(entry.getLongKey());
                        int chunkZ = ChunkPos.getZ(entry.getLongKey());
                        double centerX = (chunkX << 4) + 8.0 - camX;
                        double centerZ = (chunkZ << 4) + 8.0 - camZ;
                        if (centerX * centerX + centerZ * centerZ > 512.0 * 512.0) {
                                continue;
                        }
                        int transformIndex = transforms.size();
                        transforms.add(new DynamicGpuData.Transform(
                                levelRenderState.cameraRenderState.viewRotationMatrix, white,
                                new Vector3f((float) ((chunkX << 4) - camX), (float) (minY - camY),
                                        (float) ((chunkZ << 4) - camZ)),
                                glintMatrix));
                        final int slot = transformIndex;
                        GpuBuffer nullIndexBuffer = null;
                        IndexType nullIndexType = null;
                        draws.add(new RenderPass.Draw<>(0, chunk.vertexBuffer, nullIndexBuffer, nullIndexType, 0,
                                chunk.indexCount, 0,
                                (slices, uniformUploader) -> uniformUploader.setUniform("DynamicTransforms",
                                        slices[slot])));
                        if (chunk.indexCount > maxIndicesRequired) {
                                maxIndicesRequired = chunk.indexCount;
                        }
                }
                if (draws.isEmpty()) {
                        return;
                }

                RenderSystem.AutoStorageIndexBuffer sequentialIndexBuffer = RenderSystem
                        .getSequentialBuffer(PrimitiveTopology.QUADS);
                GpuBuffer indexBuffer = sequentialIndexBuffer.getBuffer(maxIndicesRequired);
                IndexType indexType = sequentialIndexBuffer.type();
                GpuTextureView auxView = auxTarget.getColorTextureView();

                AbstractTexture atlasTexture = Minecraft.getInstance().getTextureManager()
                        .getTexture(TextureAtlas.LOCATION_BLOCKS);
                AbstractTexture glintTexture = Minecraft.getInstance().getTextureManager()
                        .getTexture(GLINT_TEXTURE);
                try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Craftmine Sky Windows", mainTarget.getColorTextureView(), Optional.empty(),
                        mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
                        RenderSystem.bindDefaultUniforms(renderPass);
                        renderPass.setPipeline(RenderSystem.getCompiledPipeline(SkyBlockPipelines.SKY_BLOCK));
                        renderPass.setUniform("Sampler0", atlasTexture.getTextureView(), atlasTexture.getSampler());
                        renderPass.setUniform("Sampler1", auxView,
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                        renderPass.setUniform("Sampler2", glintTexture.getTextureView(), glintTexture.getSampler());
                        GpuBufferSlice[] transformSlices = RenderSystem.getDynamicUniforms()
                                .writeTransforms(transforms.toArray(new DynamicGpuData.Transform[0]));
                        renderPass.drawMultipleIndexed(draws, indexBuffer, indexType,
                                List.of("DynamicTransforms"), transformSlices);
                }
        }

        /**
         * The scrolling enchanted-glint texture matrix (two different scroll
         * periods so the pattern takes long to repeat, rotated 10 degrees,
         * scaled to 1% - identical to the snapshot's setupGlintTexturing).
         */
        public static Matrix4f setupGlintTexturing(float scale) {
                long time = (long) ((double) Util.getMillis()
                        * (Double) Minecraft.getInstance().options.glintSpeed().get() * 8.0);
                float scrollX = (float) (time % 110000L) / 110000.0f;
                float scrollY = (float) (time % 30000L) / 30000.0f;
                Matrix4f matrix = new Matrix4f().translation(-scrollX, scrollY, 0.0f);
                matrix.rotateZ(0.17453292f).scale(scale);
                return matrix;
        }
}
