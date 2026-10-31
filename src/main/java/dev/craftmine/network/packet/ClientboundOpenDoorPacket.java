package dev.craftmine.network.packet;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GamePacketTypes;
import dev.craftmine.inventory.DoorMenu;

public record ClientboundOpenDoorPacket(int containerId, List<DoorMenu.Layout> options) implements Packet<ClientGamePacketListener>
{
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenDoorPacket> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.CONTAINER_ID, ClientboundOpenDoorPacket::containerId, DoorMenu.Layout.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundOpenDoorPacket::options, ClientboundOpenDoorPacket::new);

    @Override
    public PacketType<ClientboundOpenDoorPacket> type() {
        return dev.craftmine.network.CraftmineGamePacketTypes.CLIENTBOUND_OPEN_DOOR_PACKET;
    }

    @Override
    public void handle(ClientGamePacketListener clientGamePacketListener) {
        ((dev.craftmine.network.CraftmineClientPacketDuck)clientGamePacketListener).handleOpenDoorPacket(this);
    }
}

