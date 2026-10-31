package dev.craftmine.inventory;

import dev.craftmine.duck.CraftmineSlotDuck;
import dev.craftmine.registry.CraftmineDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The minecrafter's result slot. Intentional quirk kept from the fork:
 * {@link #mayPickup} never lets the result be TAKEN - a left-click on the
 * crafted mine IS the "enter mine" action, routed to
 * {@link MineCraftingMenu#onOpenMine}, and completed/active mines refuse
 * even that.
 */
public class MineCraftingResultSlot extends Slot implements CraftmineSlotDuck {
    private final MineCraftingMenu menu;

    public MineCraftingResultSlot(Container container, int index, int x, int y, MineCraftingMenu menu) {
        super(container, index, x, y);
        this.menu = menu;
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        ItemStack itemStack = this.getItem();
        if (itemStack.has(CraftmineDataComponents.MINE_ACTIVE) || itemStack.has(CraftmineDataComponents.MINE_COMPLETED)) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            this.menu.onOpenMine(serverPlayer, itemStack);
        }
        return false;
    }

    @Override
    public boolean shouldRotate() {
        return this.menu.isMineActive();
    }
}
