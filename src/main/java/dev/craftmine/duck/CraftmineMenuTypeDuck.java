/*
 * Duck for the fork's AbstractContainerMenu.getTypeRaw() - the raw menuType
 * field without the throwing getType() guard. Implemented by
 * AbstractContainerMenuMixin so every menu (including the null-typed
 * InventoryMenu/DoorMenu) can be type-compared safely.
 *
 * Deliberately NOT a method on CraftmineMenuDuck: that duck carries real
 * craftmine menu behavior (updateData/additionalData) and instanceof checks
 * against it (CraftmineServerHooks.openCraftmineMenu) must keep matching
 * only the actual craftmine menus, never vanilla menus.
 */
package dev.craftmine.duck;

import net.minecraft.world.inventory.MenuType;

public interface CraftmineMenuTypeDuck {
    /**
     * Fork-faithful AbstractContainerMenu.getTypeRaw(): the raw menuType
     * field, null when the menu was constructed without a registered
     * MenuType.
     */
    MenuType<?> craftmine$getTypeRaw();
}
