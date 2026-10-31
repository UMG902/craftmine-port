/*
 * Task 31 (unlock effects): the player main-inventory row slot, gated by the
 * "Pockets" unlock tiers - the fork's AbstractContainerMenu gated variant
 * (cxy: the anonymous Slot added by the addStandardInventorySlots equivalent).
 *
 * Inventory row 0 (slots 9-17)  = inventory_slots_1 "Pockets"
 * Inventory row 1 (slots 18-26) = inventory_slots_2 "Deeper Pockets"
 * Inventory row 2 (slots 27-35) = inventory_slots_3 "Deepest Pockets"
 * Hotbar (0-8) is always usable.
 *
 * Both mayPlace and isActive are gated, so the rows are locked "crafter
 * style": the inventory screen draws the crafter/disabled_slot sprite over
 * them (AbstractContainerScreen client mixin) and interactions are rejected.
 * Applied in EVERY menu that shows the player's inventory (the fork adds the
 * gated variant from the shared AbstractContainerMenu helper).
 */
package dev.craftmine.inventory;

import dev.craftmine.unlock.PlayerUnlocks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CraftminePocketSlot extends Slot {
    public CraftminePocketSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    private boolean craftmine$rowUnlocked() {
        Container container = this.container;
        if (!(container instanceof Inventory inventory)) {
            return true;
        }
        Player player = inventory.player;
        int row = (this.getContainerSlot() - 9) / 9;
        return switch (row) {
            case 0 -> CraftmineUnlockGate.isActive(player, PlayerUnlocks.INVENTORY_SLOTS_1);
            case 1 -> CraftmineUnlockGate.isActive(player, PlayerUnlocks.INVENTORY_SLOTS_2);
            case 2 -> CraftmineUnlockGate.isActive(player, PlayerUnlocks.INVENTORY_SLOTS_3);
            default -> true;
        };
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return super.mayPlace(itemStack) && this.craftmine$rowUnlocked();
    }

    @Override
    public boolean isActive() {
        return super.isActive() && this.craftmine$rowUnlocked();
    }
}
