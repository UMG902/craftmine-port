package dev.craftmine.duck;

import dev.craftmine.server.CraftmineServerHooks;
import net.minecraft.core.Holder;
import dev.craftmine.unlock.PlayerUnlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Static helpers that perform the duck casts for ported craftmine code.
 * The call-site rewriter points craftmine method calls here.
 */
public final class CraftmineDuckBridge {
    private CraftmineDuckBridge() {
    }

    public static CraftmineLevelDuck level(ServerLevel level) {
        return (CraftmineLevelDuck) (Object) level;
    }

    public static CraftmineLevelDuck level(net.minecraft.world.level.Level level) {
        return (CraftmineLevelDuck) (Object) level;
    }

    public static ServerLevel levelOf(ServerPlayer player) {
        return player.level();
    }

    public static CraftminePlayerDuck player(ServerPlayer player) {
        return (CraftminePlayerDuck) (Object) player;
    }

    public static CraftminePlayerDuck player(net.minecraft.world.entity.player.Player player) {
        return (CraftminePlayerDuck) (Object) player;
    }

    /**
     * Fork-faithful Player.isActive across sides: the fork's base Player
     * returns false, its client players delegate to the synced
     * ClientPlayerUnlocks and its ServerPlayer consults the authoritative
     * state. ServerPlayer (CraftminePlayerDuck) wins first; client players
     * (CraftmineUnlockViewDuck, implemented by the client LocalPlayerMixin)
     * serve the synced view; anything else falls back to false exactly like
     * the fork's base Player. NEVER cast a nullable/generic Player straight
     * to CraftminePlayerDuck on a client-reachable path (task 49 tooltip CCE).
     */
    public static boolean isActive(Player player, Holder<PlayerUnlock> holder) {
        if (player instanceof CraftminePlayerDuck) {
            return ((CraftminePlayerDuck) (Object) player).isActive(holder);
        }
        if (player instanceof CraftmineUnlockViewDuck) {
            return ((CraftmineUnlockViewDuck) (Object) player).craftmine$isActive(holder);
        }
        return false;
    }

    /** Same dispatch as {@link #isActive} for Player.isUnlocked. */
    public static boolean isUnlocked(Player player, Holder<PlayerUnlock> holder) {
        if (player instanceof CraftminePlayerDuck) {
            return ((CraftminePlayerDuck) (Object) player).isUnlocked(holder);
        }
        if (player instanceof CraftmineUnlockViewDuck) {
            return ((CraftmineUnlockViewDuck) (Object) player).craftmine$isUnlocked(holder);
        }
        return false;
    }
}
