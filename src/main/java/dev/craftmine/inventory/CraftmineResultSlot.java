/*
 * Task 31 (unlock effects): crafting result slot, gated on the root
 * "Crafting" unlock + the dynamic per-menu flag (see CraftmineCraftingSlot).
 * The fork gates the result slot of the inventory 2x2 AND the crafting table
 * 3x3 identically (cxz anonymous dab subclass), so without the "Crafting"
 * unlock no crafting is possible anywhere - you start with only the hotbar
 * and no crafting at all.
 */
package dev.craftmine.inventory;

import dev.craftmine.duck.CraftmineGateSlot;
import dev.craftmine.unlock.PlayerUnlocks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;

public class CraftmineResultSlot extends ResultSlot implements CraftmineGateSlot {
    private final Player craftmine$player;
    private boolean craftmine$unlockActive = true;

    public CraftmineResultSlot(Player player, CraftingContainer craftSlots, Container container, int id, int x, int y) {
        super(player, craftSlots, container, id, x, y);
        this.craftmine$player = player;
    }

    @Override
    public void craftmine$setUnlockActive(boolean active) {
        this.craftmine$unlockActive = active;
    }

    @Override
    public boolean craftmine$isUnlockFlagSet() {
        return this.craftmine$unlockActive;
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return false;
    }

    @Override
    public boolean isActive() {
        return super.isActive()
            && this.craftmine$unlockActive
            && CraftmineUnlockGate.isActive(this.craftmine$player, PlayerUnlocks.CRAFTING);
    }
}
