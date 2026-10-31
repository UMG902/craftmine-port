package dev.craftmine.network.packet;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.world.level.dimension.DimensionType;

public record ClientboundChangeDimensionTypePacket(Holder<DimensionType> dimensionType) implements Packet<ClientGamePacketListener>
{
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundChangeDimensionTypePacket> STREAM_CODEC = StreamCodec.composite(DimensionType.STREAM_CODEC, ClientboundChangeDimensionTypePacket::dimensionType, ClientboundChangeDimensionTypePacket::new);

    @Override
    public PacketType<ClientboundChangeDimensionTypePacket> type() {
        return dev.craftmine.network.CraftmineGamePacketTypes.CLIENTBOUND_CHANGE_DIMENSION_TYPE;
    }

    @Override
    public void handle(ClientGamePacketListener clientGamePacketListener) {
        ((dev.craftmine.network.CraftmineClientPacketDuck)clientGamePacketListener).handleChangeDimensionType(this);
    }
}

