package dev.craftmine.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.CubeMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.craftmine.client.render.TitleRain;
import dev.craftmine.snapshot.CraftmineSnapshotLocator;

/**
 * Replaces the menu panorama with the snapshot's green code rain - on the
 * title screen (sharp) AND behind every other panorama screen: out-of-world
 * menus (Select World, Options, Create World, Connect, ...) AND the loading
 * screens (LevelLoadingScreen OTHER, GenericMessageScreen "Saving world"),
 * where 26.3's own blur stratum turns it into the dim soft-glyph backdrop the
 * real snapshot shows.
 *
 * <p>26.3's {@code GuiRenderer.render()} draws {@code cubeMap.render(10.0F,
 * spin)} whenever the frame's {@code panoramaRenderState} is non-null, i.e.
 * the current screen registered a panorama. With the snapshot asset pack
 * active the pack's panorama textures are 1x1 blank placeholders (RGB
 * 98,111,113), so the stock path shows a flat color where the real snapshot
 * had its animated rain; the rain is dimmed for non-title screens by the
 * snapshot pack's dark {@code textures/gui/menu_background.png} veil
 * (vanilla's own veil is only 25% black - the snapshot's is ~87%).
 *
 * <p>NO level guard here, deliberately: the redirect call site only executes
 * when a panorama was registered, and the panorama screens in 26.3
 * (TitleScreen, LevelLoadingScreen, GenericMessageScreen - constant-pool
 * audited) draw the cube FULLSCREEN INSTEAD of the world, so intercepting the
 * call never covers gameplay. The earlier {@code level == null} guard WAS the
 * gray-flash bug (batch AB): LevelLoadingScreen and GenericMessageScreen
 * register a panorama while the level already exists (join/mine-entry load,
 * world save), so the guard quit mid-load and vanilla drew the 1x1 placeholder
 * fullscreen, darkened ~25% by the blur stratum = the flat #4A5355 gray the
 * user recorded (panorama placeholder 98,111,113 x 0.75 = 74,83,85).
 *
 * <p>The static block registers the rain pipeline at mixin-apply time, before
 * the resource reload snapshots the pipeline list - the same boot-order trick
 * SkyRendererMixin uses for the sky pipelines (late registration would fall
 * into the fallback pipeline cache whose shader provider only sees the
 * vanilla pack, and core/title_rain would fail to resolve).
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    static {
        if (TitleRain.TITLE_RAIN == null || !(TitleRain.TITLE_RAIN instanceof RenderPipeline)) {
            throw new IllegalStateException("TitleRain pipeline failed to initialize");
        }
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/CubeMap;render(FF)V"
            )
    )
    private void craftmine$titleCodeRainInsteadOfPanorama(CubeMap instance, float pitch, float spin) {
        // No level guard: vanilla's call site only fires when the frame's screen
        // registered a panorama (renderState.panoramaRenderState != null), and
        // every panorama screen in 26.3 replaces the world view with the cube
        // fullscreen. While a loading screen is up the level usually ALREADY
        // exists (the ClientLevel spawns mid-load), which is exactly when the
        // old level == null guard bailed to the stock path and produced the
        // flat placeholder gray. TitleRain.draw falls back to the vanilla cube
        // itself if the rain ever fails, so behavior without the snapshot pack
        // or on rain failure is unchanged.
        if (CraftmineSnapshotLocator.locate() != null) {
            TitleRain.draw(instance, pitch, spin);
        } else {
            instance.render(pitch, spin);
        }
    }
}
