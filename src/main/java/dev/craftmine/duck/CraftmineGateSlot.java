/*
 * Task 31 (unlock effects): implemented by the craftmine gated slot classes.
 * The unlock-driven active flag is kept ON the slot instances and toggled by
 * InventoryMenu.craftmine$updateUnlockSlots (the fork's cze.e(player) port).
 *
 * The unlock state combines as: mayPlace/isActive = root-check && flag,
 * where the root-check (e.g. the "Crafting" root unlock) is evaluated fresh
 * on every query and the flag reflects the per-menu unlock layout
 * (inventory crafting 2x2 / 3x3). Default flag = true, so menus without a
 * dynamic layout (the crafting table) only depend on the root check -
 * exactly the fork's cxz behaviour.
 */
package dev.craftmine.duck;

public interface CraftmineGateSlot {
    void craftmine$setUnlockActive(boolean active);

    boolean craftmine$isUnlockFlagSet();
}
