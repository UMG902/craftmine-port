/*
 * Craftmine additions to ClientPacketListener: the client player-unlocks
 * tracker (original: ClientPacketListener.unlocks field + getUnlocks()).
 */
package dev.craftmine.duck;

import dev.craftmine.unlock.ClientPlayerUnlocks;

public interface CraftmineClientUnlocksDuck {
    ClientPlayerUnlocks craftmine$getUnlocks();
}
