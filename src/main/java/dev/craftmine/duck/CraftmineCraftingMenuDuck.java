/*
 * Task 31 (unlock effects): dynamic crafting-grid unlock layout, ported from
 * the fork's cze.e(player). Implemented by the InventoryMenu overlay;
 * called every tick from the client InventoryScreen (fork-faithful) and from
 * the server ServerPlayer tick while the inventory screen is open (the fork
 * left the server-side flag stale; the port keeps it fresh so locked slots
 * are also server-authoritative).
 */
package dev.craftmine.duck;

public interface CraftmineCraftingMenuDuck {
    void craftmine$updateUnlockSlots();
}
