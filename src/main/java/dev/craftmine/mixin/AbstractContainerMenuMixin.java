/*
 * Task 31 (unlock effects): the player main-inventory rows are locked
 * "crafter style" until the Pockets unlocks are bought. The fork implements
 * this inside its AbstractContainerMenu: the standard-inventory-slots helper
 * creates gated Slot subclasses (cxy anonymous slots) for the 27 extended
 * rows while the hotbar stays plain.
 *
 * 26.3's AbstractContainerMenu.addInventoryExtendedSlots news up a plain
 * Slot per row slot - the redirect swaps in CraftminePocketSlot (row gating
 * on inventory_slots_1/2/3). Chests/crafting tables/furnaces all route their
 * player inventory through this helper, matching the fork (rows locked in
 * every menu). Hotbar slots come from addInventoryHotbarSlots - untouched.
 *
 * Task 50 (menu-type crash): also ducks the fork's
 * AbstractContainerMenu.getTypeRaw() (null-safe raw menuType accessor) so
 * server code can compare a player's open menu without the throwing
 * getType() - InventoryMenu/DoorMenu carry no registered MenuType and
 * syncContainerMenuData killed the server tick whenever a mine crafter
 * dispensed rewards while a player was outside a registered menu
 * (user report 2026-10-20 16:40).
 */
package dev.craftmine.mixin;

import dev.craftmine.duck.CraftmineMenuTypeDuck;
import dev.craftmine.inventory.CraftminePocketSlot;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin implements CraftmineMenuTypeDuck {

    /*
     * Task 50: vanilla's menuType field - null for menus constructed
     * without a registered MenuType (InventoryMenu, DoorMenu).
     */
    @Shadow
    @Final
    private MenuType<?> menuType;

    /** Fork-faithful AbstractContainerMenu.getTypeRaw(). */
    @Unique
    @Override
    public MenuType<?> craftmine$getTypeRaw() {
        return this.menuType;
    }

    @Redirect(
        method = "addInventoryExtendedSlots",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/Container;III)Lnet/minecraft/world/inventory/Slot;"
        )
    )
    private Slot craftmine$gatedPocketSlot(Container container, int index, int x, int y) {
        if (container instanceof Inventory) {
            return new CraftminePocketSlot(container, index, x, y);
        }
        return new Slot(container, index, x, y);
    }
}
