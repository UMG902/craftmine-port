/*
 * 26.3 helper: Level.setDayTime(long) was removed in favour of the WorldClock
 * system; this restores the fork's semantics on the shared overworld clock.
 */
package dev.craftmine.server;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

public final class CraftmineTime {
    private CraftmineTime() {
    }

    public static void setDayTime(ServerLevel level, long ticks) {
        Holder<WorldClock> clock = level.dimensionType().defaultClock().orElse(null);
        if (clock == null) {
            // 26.3 API divergence: the OVERWORLD dimension type has NO default_clock
            // (its JSON entry is null) - the shared day-time clock is the registered
            // WorldClocks.OVERWORLD. Mine levels reference that same clock through the
            // "craftmine:generated" dimension type (default_clock = "minecraft:overworld"),
            // so the fork's single global day time still exists - but the old call shape
            // (setDayTime(theGame().overworld(), ...)) silently no-op'ed on the empty
            // Optional, which is why ETERNAL_NIGHT/KUIPER_WORLD never darkened their mine
            // and the onMineEntered/onMineLeave morning reset never fired. Resolve the
            // shared clock explicitly whenever the level itself carries no default clock.
            clock = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
        }
        level.clockManager().setTotalTicks(clock, ticks);
    }

    public static long getDayTime(ServerLevel level) {
        return level.getDefaultClockTime();
    }
}
