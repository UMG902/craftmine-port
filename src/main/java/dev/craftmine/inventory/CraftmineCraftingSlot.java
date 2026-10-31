/*
 * Task 31 (unlock effects): crafting-grid input slot, gated on the root
 * "Crafting" unlock (fresh check, the fork's cxz anonymous slot) combined
 * with the dynamic per-menu flag (InventoryMenu.craftmine$updateUnlockSlots:
 * nothing without "Inventory Crafting", the top-left 2x2 subset with it, all
 * 9 slots with "3x3 Inventory Crafting"). Flag defaults to true so the
 * crafting table menu (no dynamic layout) only depends on the root check -
 * the fork's cxz behaviour.
 */
package dev.craftmine.inventory;

import dev.craftmine.duck.CraftmineGateSlot;
import dev.craftmine.unlock.PlayerUnlocks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CraftmineCraftingSlot extends Slot implements CraftmineGateSlot {
    private final Player craftmine$player;
    private boolean craftmine$unlockActive = true;

    public CraftmineCraftingSlot(Container container, int index, int x, int y, Player player) {
        super(container, index, x, y);
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
        return super.mayPlace(itemStack)
            && this.craftmine$unlockActive
            && CraftmineUnlockGate.isActive(this.craftmine$player, PlayerUnlocks.CRAFTING);
    }

    @Override
    public boolean isActive() {
        return super.isActive()
            && this.craftmine$unlockActive
            && CraftmineUnlockGate.isActive(this.craftmine$player, PlayerUnlocks.CRAFTING);
    }
}
