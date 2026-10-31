package dev.craftmine.duck;

import net.minecraft.core.Holder;
import dev.craftmine.unlock.PlayerUnlock;

/**
 * Cross-side read view of the craftmine unlock state on a Player.
 *
 * Fork equivalent: Player.isActive/isUnlocked exist natively on the fork's
 * Player - base Player returns false, the fork's client players
 * (LocalPlayer/RemotePlayer) override them with the synced ClientPlayerUnlocks,
 * and the fork's ServerPlayer override consults the authoritative state.
 *
 * The port keeps the authoritative state on CraftminePlayerDuck (ServerPlayer
 * only); client players implement THIS interface instead, backed by the synced
 * ClientPlayerUnlocks. Use CraftmineDuckBridge.isActive/isUnlocked at call
 * sites - never cast to CraftminePlayerDuck on a client-reachable path.
 */
public interface CraftmineUnlockViewDuck {
    boolean craftmine$isUnlocked(Holder<PlayerUnlock> holder);

    boolean craftmine$isActive(Holder<PlayerUnlock> holder);
}
