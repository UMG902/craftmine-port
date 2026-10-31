package dev.craftmine.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import dev.craftmine.client.screen.CraftmineDisabledScreen;
import dev.craftmine.snapshot.CraftmineSnapshotLocator;
import dev.craftmine.snapshot.CraftmineSnapshotPackSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Title-screen diagnostics for the snapshot asset pack. Two cases:
 * <ul>
 * <li>pack active - the CRAFTMINE logo (and splashes, sounds) are served
 * from the user's installed 25w14craftmine snapshot jar; log the fact once;</li>
 * <li>pack not active - keep the vanilla logo and show a persistent, clean
 * notice telling the user how to provide it (the "bring your own files"
 * model needs the snapshot present for the CRAFTMINE logo, splashes and
 * some sounds).</li>
 * </ul>
 */
@Mixin(TitleScreen.class)
public abstract class CraftmineTitleScreenMixin extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("craftmine/snapshot");

    protected CraftmineTitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void craftmine$warnIfSnapshotMissing(CallbackInfo ci) {
        boolean packActive = isSnapshotPackSelected();
        LOGGER.info("Craftmine: title screen ready - snapshot asset pack active: {} (packs: {})",
                packActive,
                Minecraft.getInstance().getResourcePackRepository().getSelectedIds());
        if (packActive) {
            return;
        }
        // Full-screen handoff: without the snapshot the mod has nothing to run
        // on - all of its art, sounds and texts load from the user's own
        // snapshot jar at runtime. Scheduled on the client thread because
        // opening a screen from inside TitleScreen.init would re-enter
        // setScreen while the title screen is still building its widgets.
        Minecraft.getInstance().execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() == (Object) this) {
                minecraft.gui.setScreen(new CraftmineDisabledScreen());
            }
        });
    }

    /**
     * The definitive signal: whether the repository's selected pack list
     * actually contains "craftmine/snapshot". If this logs {@code false}
     * while the locator found the jar, registration failed; if it logs
     * {@code true} while the logo still shows MINECRAFT, the blocklist or
     * the pack's resource serving is at fault.
     */
    private static boolean isSnapshotPackSelected() {
        try {
            return Minecraft.getInstance()
                    .getResourcePackRepository()
                    .getSelectedIds()
                    .contains(CraftmineSnapshotPackSource.PACK_ID);
        } catch (Throwable t) {
            LOGGER.warn("Craftmine: could not query resource-pack repository", t);
            return false;
        }
    }
}
