/*
 * Syncs the active sky override of one dimension to clients (Dimension
 * Control pick).  Empty sky = no override, client falls back to its default.
 */
package dev.craftmine.network.packet;

import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;
import dev.craftmine.level.DimensionSpecialEffects;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

public record ClientboundSetSkyPacket(Identifier dimension, Optional<DimensionSpecialEffects.Sky> sky)
    implements Packet<ClientGamePacketListener> {

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSetSkyPacket> STREAM_CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC,
        ClientboundSetSkyPacket::dimension,
        ByteBufCodecs.optional(DimensionSpecialEffects.Sky.STREAM_CODEC),
        ClientboundSetSkyPacket::sky,
        ClientboundSetSkyPacket::new
    );

    @Override
    public PacketType<ClientboundSetSkyPacket> type() {
        return dev.craftmine.network.CraftmineGamePacketTypes.CLIENTBOUND_SET_SKY;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        ((dev.craftmine.network.CraftmineClientPacketDuck) listener).handleSetSky(this);
    }
}
