package dev.craftmine.inventory;

import dev.craftmine.duck.CraftmineSlotDuck;
import dev.craftmine.item.component.WorldModifiers;
import dev.craftmine.mines.WorldEffect;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineDataComponents;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * One spiral ingredient slot of the minecrafter. Random slots (rolled by
 * {@link MineCraftingMenu#populateRandomEffects}) and all slots lock as soon
 * as a mine is active/completed or in boss mines; hand-placed slots only
 * accept mine ingredients whose effects are valid with the current grid.
 */
public class MineCraftingSlot extends Slot implements CraftmineSlotDuck {
    public MineCraftingMenu menu;
    public boolean randomSlot;
    private boolean craftmine$active = true;

    public void setActive(boolean active) {
        this.craftmine$active = active;
    }

    public MineCraftingSlot(Container container, int index, int x, int y, MineCraftingMenu menu, boolean randomSlot) {
        super(container, index, x, y);
        this.randomSlot = randomSlot;
        this.menu = menu;
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        // isMineCompleted lives on the menu itself (result-slot component check);
        // the CFR decompile emitted a bogus CraftmineLevelDuck cast here which
        // threw ClassCastException as soon as the screen rendered.
        if (this.menu.isMineActive() || this.menu.isMineCompleted() || this.menu.isBossMine() || this.randomSlot) {
            return false;
        }
        if (!itemStack.is(CraftmineContent.MINE_INGREDIENT)) {
            return false;
        }
        WorldModifiers worldModifiers = itemStack.get(CraftmineDataComponents.WORLD_MODIFIERS);
        if (worldModifiers == null) {
            return false;
        }
        List<WorldEffect> currentEffects = this.menu.getCraftingEffects().toList();
        return worldModifiers.effects().stream().allMatch(worldEffect -> worldEffect.isValidWith(currentEffects));
    }

    @Override
    public boolean mayPickup(Player player) {
        if (this.menu.isMineActive() || this.menu.isMineCompleted() || this.menu.isBossMine() || this.randomSlot) {
            return false;
        }
        return super.mayPickup(player);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        this.menu.slotsChanged(this.container);
    }

    @Override
    public boolean shouldMove() {
        return this.menu.isMineActive();
    }

    @Override
    public boolean shouldRotate() {
        return this.menu.isMineActive();
    }

    @Override
    public boolean isActive() {
        return this.craftmine$active && super.isActive() && !this.menu.isMineCompleted() && !this.menu.isBossMine();
    }
}
