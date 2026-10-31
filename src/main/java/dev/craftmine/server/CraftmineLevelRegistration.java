package dev.craftmine.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Task 64: CRASH FIX - java.util.ConcurrentModificationException in
 * MinecraftServer.tickChildren when entering the FIRST freshly-crafted mine
 * of a session (user: "when entering the second mine a crash happened"; the
 * crash report shows LinkedHashMap$LinkedHashIterator.nextNode inside
 * tickChildren's level loop).
 *
 * WHY IT CRASHED: vanilla tickChildren iterates MinecraftServer.levels.values()
 * (a LinkedHashMap) while ticking every level. The mine-enter countdown runs
 * from ServerLevelMixin.craftmine$tickMine -> handleInProgressMine -> the
 * "Entering Mine in: N seconds" counter, i.e. in the MIDDLE of that iteration.
 * When the countdown expires, leaveForMine -> CraftmineLevelCreation
 * .ensureGeneratedLevel -> TheGame.createGeneratedLevel registered the freshly
 * constructed mine level in the vanilla level map IMMEDIATELY. Adding a NEW
 * key to a LinkedHashMap structurally modifies it under the open iterator, and
 * the very next iteration step threw the CME and took the whole server down.
 *
 * The earlier tasks never hit this because the command paths (/level, rig
 * /locate probes) execute OUTSIDE the level-values loop; only the real
 * gameplay path (countdown expiry) is inside it. Re-entering a mine from a
 * previous session also never hits it (the level is re-registered at boot,
 * outside any iteration).
 *
 * HOW THE ORIGINAL AVOIDED IT: 25w14craftmine's WorldReloader tore the whole
 * TheGame down and rebuilt it between ticks, so its level map was never
 * mutated mid-iteration either.
 *
 * THE FIX: queue the registration and apply it at a proven-safe point - the
 * TAIL of MinecraftServer.tickChildren (after the level loop, the connection
 * phase, the player tick and the chunk-send phase have all finished consuming
 * their iterators; tickServer has no further level iteration after
 * tickChildren returns - verified against the 26.3 sources). Until the drain
 * the new ServerLevel is fully constructed and functional: TheGame.levels
 * already contains it (the same-tick teleport pipeline uses the object
 * directly), it is simply not TICKED until the next tickChildren - exactly
 * the between-ticks registration timing the original had.
 */
public final class CraftmineLevelRegistration {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private record QueuedLevel(ResourceKey<Level> key, ServerLevel level) {
    }

    /** Server-thread only: level construction and the tick loop share one thread. */
    private static final List<QueuedLevel> PENDING = new ArrayList<>();

    private CraftmineLevelRegistration() {
    }

    /**
     * Registers a newly constructed level in the vanilla server's level map at
     * the next safe point (tickChildren TAIL) instead of immediately. The
     * immediate-registration variant crashed the server whenever the caller ran
     * inside tickChildren's level iteration - which is every live mine entry.
     */
    public static void enqueue(MinecraftServer server, ResourceKey<Level> key, ServerLevel level) {
        PENDING.removeIf(queued -> queued.key().equals(key));
        PENDING.add(new QueuedLevel(key, level));
    }

    /**
     * Applies every queued registration. Called from MinecraftServerMixin at
     * tickChildren TAIL - the one point guaranteed to be outside every level-map
     * iteration (the loop itself, connection, players, chunk-send and the
     * serverActivityMonitor all precede it).
     */
    public static void drain(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<QueuedLevel> queued = List.copyOf(PENDING);
        PENDING.clear();
        Map<ResourceKey<Level>, ServerLevel> levels =
            ((dev.craftmine.mixin.MinecraftServerAccessor) (Object) server).craftmine$levels();
        for (QueuedLevel entry : queued) {
            if (server.getLevel(entry.key()) == null) {
                levels.put(entry.key(), entry.level());
                LOGGER.info("craftmine-level-registration: registered level {} after the tick loop (deferred to avoid tickChildren ConcurrentModificationException)",
                    entry.key().identifier());
            }
        }
    }
}
