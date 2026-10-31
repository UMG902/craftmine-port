/*
 * Client registry resync state machine.
 *
 * Mine dimensions materialize live (CraftmineRegistryMaterialization rebuilds
 * the WORLD registry layer with the mine's biomes/dimension types), but every
 * client that is already connected holds the OLD registry data — chunk palettes
 * for the mine reference biome ids the client has never seen, so the client
 * hangs forever on "Loading terrain..." (26.3 has no mid-game dimension reload
 * like the snapshot's WorldReloader, which pushed players through the
 * configuration phase for exactly this reason).
 *
 * 26.3 DOES have the full mid-game reconfiguration plumbing: the play listener
 * exposes switchToConfig(), the client acks, the listener becomes
 * ServerConfigurationPacketListenerImpl, and startConfiguration() runs the
 * registry sync (SynchronizeRegistriesTask reads MinecraftServer.registries())
 * + PrepareSpawnTask + JoinWorldTask — the player re-enters the world with
 * fresh registry data. Vanilla only ever calls startConfiguration() from the
 * LOGIN path though, so this machine drives the whole sequence itself:
 *
 *   materialization -> mark online players stale -> (mine entry: teleport
 *   first, see leaveForMine) -> switchToConfig() per stale player ->
 *   startConfiguration() on each swapped listener -> player rejoins from
 *   saved data (already inside the mine) -> run the deferred action
 *   (usually empty since task 30).
 *
 * Ticking: the machine is ticked from MinecraftServer.tickConnection (see
 * MinecraftServerMixin) because that is the only method that runs on every
 * server path — IntegratedServer pauses the whole tick (empty player list,
 * which a full reconfiguration causes) and never reaches tickServer again,
 * while tickPaused()/the empty-tick branch still call tickConnection().
 */
package dev.craftmine.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;

public final class CraftmineClientResync {
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Players known to hold stale registry data: uuid -> generation they last synced. */
    private static final Map<UUID, Integer> STALE = new ConcurrentHashMap<>();
    /** Connections this machine switched into configuration, with the player they belong to. */
    private static final Map<Connection, UUID> SWITCHED = new ConcurrentHashMap<>();
    /** Switched connections whose configuration sequence we already started. */
    private static final Set<Connection> STARTED = ConcurrentHashMap.newKeySet();
    private static volatile boolean ACTIVE;
    private static MinecraftServer activeServer;
    private static Runnable pendingAction;

    private CraftmineClientResync() {
    }

    /**
     * After a registry materialization: every currently connected client now
     * holds stale registry data. Players joining from now on receive the fresh
     * data at login and are never marked.
     */
    public static void markOnlinePlayersStale(MinecraftServer server) {
        int generation = CraftmineServerHooks.theGame().craftmine$registryGeneration();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            STALE.put(player.getUUID(), generation);
        }
        LOGGER.info("craftmine-resync: marked {} online client(s) stale for registry generation {}", server.getPlayerList().getPlayerCount(), generation);
    }

    /**
     * Runs the action once every stale client is back in the play phase with
     * fresh registry data. If no connected client is stale, runs immediately.
     */
    public static void runWhenSynced(MinecraftServer server, Runnable action) {
        // Players who went offline since the materialization will login fresh.
        STALE.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        if (STALE.isEmpty()) {
            action.run();
            return;
        }
        LOGGER.info("craftmine-resync: starting client registry resync for {} client(s)", STALE.size());
        activeServer = server;
        pendingAction = action;
        ACTIVE = true;
    }

    /** Ticked from MinecraftServer.tickConnection — every server path. */
    public static void tick(MinecraftServer server) {
        if (!ACTIVE) {
            return;
        }
        if (activeServer != server) {
            // World change with leftover state: drop it.
            reset();
            return;
        }
        // 1. Drop connections that died mid-resync (their players will login fresh).
        SWITCHED.keySet().removeIf(connection -> !connection.isConnected());
        STARTED.retainAll(SWITCHED.keySet());
        // 2. Rejoin detection — ONLY for connections this machine switched. A
        //    switched player can only reappear in the player list by completing
        //    the configuration phase (registry sync + JoinWorldTask), so their
        //    presence proves freshness. Checking arbitrary stale players here
        //    would clear the mark of players we have not switched yet (they are
        //    obviously still in the player list at machine start).
        for (Map.Entry<Connection, UUID> entry : SWITCHED.entrySet()) {
            if (server.getPlayerList().getPlayer(entry.getValue()) != null) {
                STALE.remove(entry.getValue());
                STARTED.remove(entry.getKey());
                SWITCHED.remove(entry.getKey());
                break; // concurrent modification: restart the pass next tick
            }
        }
        // 3. Switch every stale player that is still in the play phase and not
        //    already being tracked (switchToConfig removes them from the world,
        //    so a tracked player never appears here again until they rejoin).
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (!STALE.containsKey(player.getUUID()) || SWITCHED.containsValue(player.getUUID())) {
                continue;
            }
            ServerGamePacketListenerImpl playListener = player.connection;
            Connection connection = playListener.connection;
            if (connection.isConnected() && connection.getPacketListener() instanceof ServerGamePacketListenerImpl) {
                SWITCHED.put(connection, player.getUUID());
                playListener.switchToConfig();
            }
        }
        // 4. The client acked and the listener became the configuration listener:
        //    vanilla only starts its task queue from the login path, so start it here.
        for (Map.Entry<Connection, UUID> entry : SWITCHED.entrySet()) {
            Connection connection = entry.getKey();
            PacketListener listener = connection.getPacketListener();
            if (listener instanceof ServerConfigurationPacketListenerImpl config && STARTED.add(connection)) {
                LOGGER.info("craftmine-resync: starting configuration for {}", entry.getValue());
                config.startConfiguration();
            }
        }
        // 5. All stale clients synced: run the deferred action. Since task 30 the
        //    teleport happens BEFORE the config switch (leaveForMine), the rejoin
        //    itself lands the client in the mine and there is no post-rejoin
        //    teleport to settle for - the old 60-tick delay (3s of dead time on
        //    every entry) is gone.
        if (STALE.isEmpty()) {
            ACTIVE = false;
            SWITCHED.clear();
            STARTED.clear();
            Runnable action = pendingAction;
            pendingAction = null;
            LOGGER.info("craftmine-resync: all clients synced, running deferred action");
            if (action != null) {
                action.run();
            }
        }
    }

    private static void reset() {
        ACTIVE = false;
        STALE.clear();
        SWITCHED.clear();
        STARTED.clear();
        pendingAction = null;
        activeServer = null;
    }

    /**
     * Task 32 fix: while a connection is switched into the configuration phase,
     * its OLD play listener must not send anything. switchToConfig() swaps the
     * outbound protocol as an IN-BAND pipeline task: every play packet submitted
     * after that task encodes against the CONFIGURATION dispatch codec and throws
     * "EncoderException: Sending unknown packet" (build 30/31 live crash: the
     * play listener's tick() has no waitingForSwitchToConfig guard, and
     * tickPlayer -> ServerPlayer.doTick re-syncs health/experience right after the
     * cross-dimension teleport reset them - those sends landed behind the protocol
     * swap and killed the connection with "Disconnected").
     *
     * The play listener's tick() is frozen for exactly this window (switch sent ->
     * client acks -> vanilla replaces the listener with the configuration listener,
     * which stops play ticks on its own). Everything the frozen tick would have
     * sent (health/experience sync, block-changed ack, keepalive) is re-sent by
     * placeNewPlayer on the rejoin.
     */
    public static boolean isPlayTickFrozen(Connection connection) {
        return SWITCHED.containsKey(connection);
    }

    /** Debug/diagnostics helper: whether a resync cycle is currently running. */
    public static boolean isActive() {
        return ACTIVE;
    }
}
