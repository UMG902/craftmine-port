package dev.craftmine.duck;

import org.joml.Matrix4fc;

import com.mojang.blaze3d.pipeline.RenderTarget;

import dev.craftmine.client.render.SkyBlockDrawList;

/**
 * Duck interface for {@code LevelRenderer} exposing the sky-block draw pass
 * (mirrors the Exotelcraft reference's {@code rCI} accessor interface).
 */
public interface SkyBlockLevelRendererDuck {

	/**
	 * The render target the sky-block layer samples as its "live sky"
	 * (Sampler1) and draws into - 26.3's main level color target.
	 */
	RenderTarget craftmine$getSkyTarget();

	/**
	 * Walks every visible section's {@code SKY_BLOCK} mesh and packs the
	 * draws (vertex/index buffer slices + per-draw glint transform) into a
	 * ready-to-run {@link SkyBlockDrawList}. Returns {@code null} when there
	 * is no sky-block geometry to draw.
	 */
	SkyBlockDrawList craftmine$prepareSkyBlockDraw(Matrix4fc viewRotationMatrix, double camX, double camY,
		double camZ);
}
