/*
 * Task 31 (unlock effects): locked slots render "crafter style" - the fork's
 * AbstractContainerScreen (geg) draws the crafter/disabled_slot sprite over
 * every inactive slot instead of hiding it (only the creative screen hides
 * them, fork: `if (this instanceof gfq) continue`).
 *
 * 26.3's extractSlots simply skips inactive slots; the TAIL inject draws the
 * disabled sprite for each of them afterwards. Hover/click handling already
 * skips inactive slots in 26.3 (isHovering loop), and the server rejects
 * interactions on locked slots authoritatively.
 */
package dev.craftmine.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Unique
    private static final Identifier craftmine$DISABLED_SLOT = Identifier.withDefaultNamespace("container/crafter/disabled_slot");

    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Inject(method = "extractSlots", at = @At("TAIL"))
    private void craftmine$renderLockedSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if ((Object) this instanceof CreativeModeInventoryScreen) {
            return;
        }
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) continue;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, craftmine$DISABLED_SLOT, slot.x - 1, slot.y - 1, 18, 18);
        }
    }
}
