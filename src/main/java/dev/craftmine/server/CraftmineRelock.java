package dev.craftmine.server;

import dev.craftmine.CraftmineConfig;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.duck.CraftminePlayerDuck;
import net.minecraft.core.Holder;
import dev.craftmine.unlock.ServerPlayerUnlocks;
import net.minecraft.server.level.ServerPlayer;
import dev.craftmine.unlock.PlayerUnlock;
import dev.craftmine.unlock.PlayerUnlocks;

/**
 * Rules for the unoriginal "relock" feature (ctrl+click an unlock to take it back).
 *
 * <ul>
 *   <li>needs the unoriginal_features config plus the relock sub-toggle ({@link CraftmineConfig#relockTree()}) and the base relock unlock ({@link PlayerUnlocks#QOL_RELOCK});</li>
 *   <li>only in the hub (buying still works anywhere, relocking does not);</li>
 *   <li>tab roots and the base relock unlock itself can never be relocked;</li>
 *   <li>an unlock with any unlocked child (tree child, or an extra unlock gated on it) cannot be
 *   relocked until every one of those children has been relocked first;</li>
 *   <li>xp refund (levels): none by default, half the price with {@link PlayerUnlocks#QOL_RELOCK_HALF},
 *   the full price with {@link PlayerUnlocks#QOL_RELOCK_FULL}.</li>
 * </ul>
 */
public final class CraftmineRelock {
    private CraftmineRelock() {
    }

    public enum Result {
        OK(null),
        /** Silent: config off / relock unlock missing / not unlocked - nothing sensible to tell the player. */
        DISABLED(null),
        NOT_UNLOCKED(null),
        ROOT("unlocks.relock.fail.root"),
        BASE("unlocks.relock.fail.base"),
        NOT_HUB("unlocks.relock.fail.not_hub"),
        HAS_CHILDREN("unlocks.relock.fail.children");

        private final String messageKey;

        Result(String messageKey) {
            this.messageKey = messageKey;
        }

        /** Lang key for the overlay message shown to the player, or null for a silent refusal. */
        public String messageKey() {
            return this.messageKey;
        }
    }

    public static Result check(ServerPlayer player, Holder<PlayerUnlock> holder) {
        CraftminePlayerDuck duck = (CraftminePlayerDuck) (Object) player;
        if (!CraftmineConfig.relockTree() || !duck.isActive(PlayerUnlocks.QOL_RELOCK)) {
            return Result.DISABLED;
        }
        if (!duck.isUnlocked(holder)) {
            return Result.NOT_UNLOCKED;
        }
        if (holder.value().parent().isEmpty()) {
            return Result.ROOT;
        }
        if (holder.equals(PlayerUnlocks.QOL_RELOCK)) {
            return Result.BASE;
        }
        if (((CraftmineLevelDuck) (Object) player.level()).isMine()) {
            return Result.NOT_HUB;
        }
        ServerPlayerUnlocks unlocks = duck.craftmine$playerUnlocks();
        if (unlocks.hasUnlockedChild(holder)) {
            return Result.HAS_CHILDREN;
        }
        return Result.OK;
    }

    /** Levels given back for relocking {@code holder} right now (call BEFORE relocking). */
    public static int refund(ServerPlayer player, Holder<PlayerUnlock> holder) {
        CraftminePlayerDuck duck = (CraftminePlayerDuck) (Object) player;
        int price = holder.value().unlockPrice();
        if (duck.isActive(PlayerUnlocks.QOL_RELOCK_FULL)) {
            return price;
        }
        if (duck.isActive(PlayerUnlocks.QOL_RELOCK_HALF)) {
            return price / 2;
        }
        return 0;
    }
}
