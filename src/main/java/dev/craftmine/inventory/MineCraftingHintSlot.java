package dev.craftmine.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The right-hand "locked effect" showcase grid of the minecrafter: read-only
 * preview slots for effects the player has NOT unlocked yet. Slots only light
 * up while occupied, and in boss mines only after the fight is won.
 */
public class MineCraftingHintSlot extends Slot {
    private final MineCraftingMenu menu;

    public MineCraftingHintSlot(Container container, int index, int x, int y, MineCraftingMenu menu) {
        super(container, index, x, y);
        this.menu = menu;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return false;
    }

    @Override
    public boolean isActive() {
        return super.isActive() && !this.getItem().isEmpty() && (!this.menu.isBossMine() || this.menu.isMineCompleted());
    }
}
