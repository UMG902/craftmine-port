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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Title-screen diagnostics for the snapshot asset pack. Two cases:
 * <ul>
 * <li>pack active - the CRAFTMINE logo (and splashes, sounds) are served
 * from the user's installed 25w14craftmine snapshot jar; log the fact once;</li>
 * <li>pack not active - the mod cannot run (all of its content loads from
 * the user's own snapshot jar at runtime), so hand off to the full-screen
 * {@link CraftmineDisabledScreen}, whose only option is quitting the game.</li>
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
        // snapshot jar at runtime. The handoff MUST NOT use execute()/submit():
        // from the game thread those run the task INLINE (BlockableEventLoop
        // only queues cross-thread submissions), which re-enters Gui.setScreen
        // in the middle of TitleScreen.init and races with whatever else is
        // touching the screen on modded installs. schedule() always queues,
        // so the swap happens cleanly on the next tick, once the title screen
        // is fully installed. A few retries cover transient screens (loading
        // overlays, onboarding, other mods' takeover) before giving up.
        this.craftmine$scheduleDisabledScreen(Minecraft.getInstance(), 0);
    }

    @Unique
    private void craftmine$scheduleDisabledScreen(Minecraft minecraft, int attempt) {
        minecraft.schedule(() -> {
            Screen current = minecraft.gui.screen();
            if (current instanceof CraftmineDisabledScreen) {
                return; // already showing (a previous attempt won)
            }
            if (current instanceof TitleScreen) {
                LOGGER.info("Craftmine: 25w14craftmine snapshot is required - showing the disabled screen (attempt {})", attempt + 1);
                minecraft.gui.setScreen(new CraftmineDisabledScreen());
                return;
            }
            // Not settled yet (overlay, onboarding, another mod's screen) -
            // try again next tick for ~a second before giving up.
            if (attempt < 11) {
                LOGGER.debug("Craftmine: deferring disabled screen, current screen: {} (attempt {})", current, attempt + 1);
                this.craftmine$scheduleDisabledScreen(minecraft, attempt + 1);
            } else {
                LOGGER.warn("Craftmine: gave up showing the disabled screen - current screen: {}", current);
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
