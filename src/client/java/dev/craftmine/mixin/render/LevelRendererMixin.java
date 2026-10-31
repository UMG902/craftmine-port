package dev.craftmine.mixin.render;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.craftmine.client.render.SkyWindowRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

/**
 * Registers the Craftmine SkyWindows passes on the 26.3 frame graph.
 *
 * <p>Snapshot-faithful draw order. In 25w14craftmine the sky_block surface is
 * a real chunk-mesh layer (RenderType "sky_block") listed FIRST among the six
 * chunk layers - before solid/cutout/translucent - and its shader samples the
 * color target AS IT WAS when the layer drew: sky only, no terrain yet. The
 * layer writes depth, so the main terrain pass afterwards simply depth-rejects
 * everything behind the window surfaces - that is why in the snapshot "nothing
 * renders behind" a sky wall, and it is also why the effect is temporally
 * stable (the sampled sky never varies with the scene).</p>
 *
 * <p>On 26.3 the frame graph is built as clear -> sky -> main, and
 * {@code addMainPass} picks up {@code targets.main} as its first statement.
 * Injecting at its HEAD registers our two passes in that gap, so the graph
 * serializes them exactly where the snapshot's chunk layer sat:</p>
 *
 * <ol>
 *   <li>{@code craftmine_sky_copy} - blits the target as it is after the sky
 *       pass (sky only, no terrain) into an aux copy. The copy exists because
 *       the window pass both samples and writes main - sampling the live
 *       target inside its own pass is a feedback hazard.</li>
 *   <li>{@code craftmine_sky_windows} - draws the window geometry (sky block,
 *       shimmering door, dimension control) into main color+depth, sampling
 *       the sky-only aux copy as the shader's Sampler1.</li>
 * </ol>
 *
 * <p>The "main" pass then chains after the window pass: geometry behind the
 * windows is depth-rejected (snapshot occlusion), geometry in front draws
 * over them, and nothing temporal is sampled anywhere (flicker fix).</p>
 *
 * <p>Drawing the windows AFTER main (the first 26.3 attempt) was wrong: the
 * aux copy then contained the finished scene, so the snapshot shader's
 * screen-space reprojection mix(scene, glyphs, glint) made every wall that
 * stood in front of geometry show that geometry through itself (the "hall
 * visible through the sky blocks" bug).</p>
 *
 * <p>The passes are fully mesher-agnostic: the window blocks are
 * RenderShape.INVISIBLE and all their geometry comes from SkyWindowRenderer,
 * so Sodium is supported by design.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

        @Shadow
        @Final
        private LevelTargetBundle targets;

        @Shadow
        @Final
        private LevelRenderState levelRenderState;

        @Inject(method = "addMainPass", at = @At("HEAD"))
        private void craftmine$addSkyWindowPasses(FrameGraphBuilder frame,
                FeatureRenderDispatcher.PreparedFrame featureFrame, GpuBufferSlice terrainFog,
                ChunkSectionsToRender chunkSectionsToRender, boolean consistentDepthRequired, CallbackInfo ci) {
                SkyWindowRenderer.get().registerPasses(frame, this.targets, this.levelRenderState);
        }
}
