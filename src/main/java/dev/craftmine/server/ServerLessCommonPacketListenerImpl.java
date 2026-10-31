package dev.craftmine.server;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import org.jetbrains.annotations.Nullable;

/**
 * The port's own base for shared (configuration + game) packet handling.
 * Vanilla's ServerCommonPacketListenerImpl is hard-tied to the full server
 * game listener; the hibernating hub listener needs the keep-alive /
 * latency / close bookkeeping WITHOUT the game-phase plumbing, so this
 * trimmed base re-implements that protocol state machine.
 *
 * <p>The keep-alive and latency math mirrors vanilla byte-for-byte — it is
 * wire behavior, not free-styling territory.</p>
 */
public abstract class ServerLessCommonPacketListenerImpl implements ServerCommonPacketListener {

    public static final int LATENCY_CHECK_INTERVAL = 15000;
    private static final long CLOSED_LISTENER_TIMEOUT_MS = 15000;
    private static final long KEEPALIVE_INTERVAL_MS = 15000;
    private static final Component TIMEOUT_DISCONNECTION_MESSAGE = Component.translatable("disconnect.timeout");
    static final Component DISCONNECT_UNEXPECTED_QUERY = Component.translatable("multiplayer.disconnect.unexpected_query_response");

    protected final Connection connection;
    private final boolean transferred;
    private long keepAliveTime;
    private boolean keepAlivePending;
    private long keepAliveChallenge;
    private long closedListenerTime;
    private boolean closed = false;
    private int latency;

    protected ServerLessCommonPacketListenerImpl(Connection connection, CommonListenerCookie cookie) {
        this.connection = connection;
        this.keepAliveTime = Util.getMillis();
        this.latency = cookie.latency();
        this.transferred = cookie.transferred();
    }

    private void close() {
        if (!this.closed) {
            this.closedListenerTime = Util.getMillis();
            this.closed = true;
        }
    }

    @Override
    public void handleKeepAlive(ServerboundKeepAlivePacket packet) {
        if (this.keepAlivePending && packet.getId() == this.keepAliveChallenge) {
            int roundTrip = (int) (Util.getMillis() - this.keepAliveTime);
            this.latency = (this.latency * 3 + roundTrip) / 4; // vanilla EWMA
            this.keepAlivePending = false;
        } else if (!this.isSingleplayerOwner()) {
            this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE);
        }
    }

    @Override
    public void handlePong(ServerboundPongPacket packet) {
        // no ping/pong UI in the hibernating phase
    }

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket packet) {
        // unknown custom payloads are ignored, vanilla-style
    }

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket packet) {
        // no resource pack enforced in the hibernating phase
    }

    @Override
    public void handleCookieResponse(ServerboundCookieResponsePacket packet) {
        this.disconnect(DISCONNECT_UNEXPECTED_QUERY);
    }

    /** Vanilla keep-alive cadence: ping every 15s, kick on 15s silence (or a stale close). */
    protected void keepConnectionAlive() {
        Profiler.get().push("keepAlive");
        long now = Util.getMillis();
        if (!this.isSingleplayerOwner() && now - this.keepAliveTime >= KEEPALIVE_INTERVAL_MS) {
            if (this.keepAlivePending) {
                this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE);
            } else if (this.checkIfClosed(now)) {
                this.keepAlivePending = true;
                this.keepAliveTime = now;
                this.keepAliveChallenge = now;
                this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
            }
        }
        Profiler.get().pop();
    }

    private boolean checkIfClosed(long now) {
        if (this.closed) {
            if (now - this.closedListenerTime >= CLOSED_LISTENER_TIMEOUT_MS) {
                this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE);
            }
            return false;
        }
        return true;
    }

    public void send(Packet<?> packet) {
        this.send(packet, null);
    }

    public void send(Packet<?> packet, @Nullable ChannelFutureListener sendListener) {
        if (packet.isTerminal()) {
            this.close();
        }
        boolean flushNow = this.shouldFlush();
        try {
            this.connection.send(packet, sendListener, flushNow);
        } catch (Throwable throwable) {
            CrashReport report = CrashReport.forThrowable(throwable, "Sending packet");
            CrashReportCategory category = report.addCategory("Packet being sent");
            category.setDetail("Packet class", () -> packet.getClass().getCanonicalName());
            throw new ReportedException(report);
        }
    }

    protected abstract boolean shouldFlush();

    public void disconnect(Component reason) {
        this.disconnect(new DisconnectionDetails(reason));
    }

    public abstract void disconnect(DisconnectionDetails details);

    protected abstract boolean isSingleplayerOwner();

    protected abstract GameProfile playerProfile();

    @VisibleForDebug
    public GameProfile getOwner() {
        return this.playerProfile();
    }

    public int latency() {
        return this.latency;
    }

    protected CommonListenerCookie createCookie(ClientInformation clientInformation) {
        return new CommonListenerCookie(this.playerProfile(), this.latency, clientInformation, this.transferred);
    }
}
