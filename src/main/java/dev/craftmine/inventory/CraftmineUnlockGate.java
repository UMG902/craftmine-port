/*
 * Task 31 (unlock effects): uniform per-side unlock-state query for the
 * shared inventory-gating slot classes.
 *
 * The fork (25w14craftmine) queries the player's unlock state directly from
 * the menu/slot classes (AbstractContainerMenu pocket rows, AbstractCraftingMenu
 * crafting root, InventoryMenu armor/crafting-grid gating). Those classes are
 * shared between client and server, and the two sides keep their unlock state
 * in different places in this port:
 *   - server: CraftminePlayerDuck.isActive (ServerPlayerUnlocks)
 *   - client: ClientPlayerUnlocks on the ClientPacketListener
 * (fork: both sides queried Player.isActive - the fork's Player carried the
 * state; this port does not).
 *
 * The client entrypoint (CraftmineClient) installs a client resolver at init;
 * the server path always goes through the duck.
 */
package dev.craftmine.inventory;

import dev.craftmine.duck.CraftminePlayerDuck;
import net.minecraft.core.Holder;
import dev.craftmine.unlock.PlayerUnlock;
import net.minecraft.world.entity.player.Player;

public final class CraftmineUnlockGate {
    private CraftmineUnlockGate() {
    }

    public interface Resolver {
        boolean isActive(Player player, Holder<PlayerUnlock> holder);
    }

    private static volatile Resolver clientResolver = null;

    public static void setClientResolver(Resolver resolver) {
        clientResolver = resolver;
    }

    public static boolean isActive(Player player, Holder<PlayerUnlock> holder) {
        if (player.level().isClientSide()) {
            Resolver resolver = clientResolver;
            return resolver != null && resolver.isActive(player, holder);
        }
        return ((CraftminePlayerDuck) player).isActive(holder);
    }
}
