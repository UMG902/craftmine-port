/*
 * Task 23 (batch AE): the creative screen's "Survival Inventory" tab wraps the
 * player's InventoryMenu slots with a HARDCODED vanilla-26.3 index map
 * (crafting 0-4 hidden, armor 5-8 as 2x2, main 9-35, hotbar 36-44, offhand 45).
 * The port's InventoryMenu uses the FORK's 51-slot layout (3x3 crafting grid:
 * result 0, grid 1-9, armor 10-13, main 14-40, hotbar 41-49, offhand 50 -
 * see InventoryMenuMixin), so vanilla's map scrambles the panel:
 *   - grid slots 5-8 rendered as the "armor" 2x2 around the player preview,
 *   - armor slots inside the main grid,
 *   - the "hotbar" row showing main-inventory slots 31-39,
 *   - hotbar slot 4 at the offhand position (35,20),
 *   - menu slots 46-50 (incl. the real offhand) spilling over the hotbar row
 *     (the offhand placeholder between two hotbar items in the user's
 *     screenshot).
 * The fork's own creative screen (gew, snapshot) maps the SAME 51-slot layout:
 * armor 10-13 -> 2x2, 0-9 hidden, 50 -> (35,20), else col=(i-14)%9 row=(i-14)/9
 * x=9+col*18, y = i>=41 ? 112 : 54+row*18. This inject re-applies that map at
 * TAIL of selectTab (after vanilla's wrap loop, which already produced one
 * wrapper per InventoryMenu slot in the same order) by repositioning the
 * wrappers in place - no wrapper reconstruction, so vanilla's originalSlots
 * restore path and the destroy-item slot (appended last) are untouched.
 */
package dev.craftmine.mixin;

import dev.craftmine.duck.CraftmineSlotDuck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void craftmine$forkInventorySlotLayout(CreativeModeTab tab, CallbackInfo ci) {
        if (tab.getType() != CreativeModeTab.Type.INVENTORY) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        InventoryMenu invMenu = minecraft.player.inventoryMenu;
        AbstractContainerMenu menu = ((CreativeModeInventoryScreen) (Object) this).getMenu();
        int wrapped = Math.min(invMenu.slots.size(), menu.slots.size());
        for (int i = 0; i < wrapped; i++) {
            Slot slot = menu.slots.get(i);
            int x;
            int y;
            if (i >= 10 && i < 14) {
                int pos = i - 10;
                x = 54 + (pos / 2) * 54;
                y = 6 + (pos % 2) * 27;
            } else if (i < 10) {
                x = -2000;
                y = -2000;
            } else if (i == 50) {
                x = 35;
                y = 20;
            } else {
                int col = (i - 14) % 9;
                int row = (i - 14) / 9;
                x = 9 + col * 18;
                y = i >= 41 ? 112 : 54 + row * 18;
            }
            ((CraftmineSlotDuck) slot).craftmine$setPosition(x, y);
        }
    }
}
