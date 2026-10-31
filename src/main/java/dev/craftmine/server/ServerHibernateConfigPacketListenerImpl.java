package dev.craftmine.server;

import com.mojang.authlib.GameProfile;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ServerboundAcceptCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * The listener a player hangs in while their mine is hibernating (the hub
 * world keeps everyone in the configuration phase, so idle players cost
 * the server nothing). Answers nothing configuration-related; the moment
 * the mine wakes up, {@link #unfreeze(TheGame)} swaps this listener for a
 * real {@link ServerConfigurationPacketListenerImpl} and configuration
 * proceeds normally.
 */
public class ServerHibernateConfigPacketListenerImpl
    extends ServerLessCommonPacketListenerImpl
    implements ServerConfigurationPacketListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Executor mainThreadExecutor;
    private final GameProfile gameProfile;
    private ClientInformation clientInformation;

    public ServerHibernateConfigPacketListenerImpl(Connection connection, CommonListenerCookie cookie, Executor mainThreadExecutor) {
        super(connection, cookie);
        this.mainThreadExecutor = mainThreadExecutor;
        this.gameProfile = cookie.gameProfile();
        this.clientInformation = cookie.clientInformation();
    }

    @Override
    public void handleConfigurationFinished(ServerboundFinishConfigurationPacket packet) {
        LOGGER.warn("Unexpected packet {}", packet.type());
    }

    @Override
    public void handleSelectKnownPacks(ServerboundSelectKnownPacks packet) {
        // hibernating: no datapack negotiation yet
    }

    @Override
    public void handleClientInformation(ServerboundClientInformationPacket packet) {
        this.clientInformation = packet.information();
    }

    @Override
    public void handleAcceptCodeOfConduct(ServerboundAcceptCodeOfConductPacket packet) {
        // hibernating listener: nothing to finish
    }

    @Override
    public void handleCustomClickAction(ServerboundCustomClickActionPacket packet) {
        // hibernating listener: ignore
    }

    @Override
    protected boolean shouldFlush() {
        return true;
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
        this.connection.send(new ClientboundDisconnectPacket(details.reason()),
            PacketSendListener.thenRun(() -> this.connection.disconnect(details)));
        this.connection.setReadOnly();
        CompletableFuture.runAsync(this.connection::handleDisconnection, this.mainThreadExecutor).join();
    }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        LOGGER.info("{} lost connection: {}", this.gameProfile, details.reason().getString());
    }

    @Override
    public boolean isAcceptingMessages() {
        return false;
    }

    @Override
    protected boolean isSingleplayerOwner() {
        return false;
    }

    @Override
    protected GameProfile playerProfile() {
        return this.gameProfile;
    }

    /** Wakes the player up: hands the connection to a fresh configuration listener. */
    public ServerConfigurationPacketListenerImpl unfreeze(TheGame game) {
        return new ServerConfigurationPacketListenerImpl(game.server(), this.connection, this.createCookie(this.clientInformation));
    }
}
