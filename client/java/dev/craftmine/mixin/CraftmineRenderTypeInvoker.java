package dev.craftmine.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes RenderType.create(String, RenderSetup) (package-private in 26.3) to
 * craftmine client code - used by CraftmineGlints to materialize the fork's two
 * extra glint render types (map won/lost).
 */
@Mixin(RenderType.class)
public interface CraftmineRenderTypeInvoker {
	@Invoker("create")
	static RenderType craftmine$create(String string, RenderSetup renderSetup) {
		throw new AssertionError();
	}
}
