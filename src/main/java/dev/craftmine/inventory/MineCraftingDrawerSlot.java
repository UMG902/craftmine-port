package dev.craftmine.inventory;

import dev.craftmine.item.component.WorldModifiers;
import dev.craftmine.mines.WorldEffect;
import dev.craftmine.registry.CraftmineDataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Drawer rows under the crafting grid: the player's unlocked effect library.
 * Taking an effect out keeps a 1-count template in the drawer
 * ({@link #onTake}) so the library is never consumed, and dropping a stack
 * onto the template ({@link #setByPlayer}) keeps it at one as well. A drawer
 * effect may only be pulled while it stays valid for the mine being crafted.
 */
public class MineCraftingDrawerSlot extends Slot {
    final MineCraftingMenu menu;

    public MineCraftingDrawerSlot(Container container, int index, int x, int y, MineCraftingMenu menu) {
        super(container, index, x, y);
        this.menu = menu;
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return true;
    }

    @Override
    public boolean mayPickup(Player player) {
        boolean compatible = this.couldBeAddedToMine();
        if (!compatible) {
            this.menu.setCarried(ItemStack.EMPTY);
        }
        return super.mayPickup(player) && compatible;
    }

    /** The stored effect exists and does not clash with the effects already in the crafting grid. */
    public boolean couldBeAddedToMine() {
        if (this.getItem().isEmpty()) {
            return true;
        }
        WorldModifiers worldModifiers = this.getItem().get(CraftmineDataComponents.WORLD_MODIFIERS);
        if (worldModifiers == null) {
            return false;
        }
        return this.menu.getCraftingEffects().allMatch(worldEffect -> worldEffect.isValidWith(worldModifiers.effects()))
            && this.menu.getCraftingEffects().noneMatch(worldModifiers.effects()::contains);
    }

    @Override
    public void onTake(Player player, ItemStack itemStack) {
        this.set(itemStack.copyWithCount(1));
    }

    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        if (!oldStack.isEmpty()) {
            this.set(oldStack.copyWithCount(1));
        }
    }

    @Override
    public boolean isActive() {
        return super.isActive() && (!this.menu.isBossMine() || this.menu.isMineCompleted());
    }
}
