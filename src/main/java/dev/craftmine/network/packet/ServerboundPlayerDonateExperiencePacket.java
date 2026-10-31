package dev.craftmine.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.network.protocol.game.ServerGamePacketListener;

public record ServerboundPlayerDonateExperiencePacket() implements Packet<ServerGamePacketListener>
{
    public static final ServerboundPlayerDonateExperiencePacket INSTANCE = new ServerboundPlayerDonateExperiencePacket();
    public static final StreamCodec<ByteBuf, ServerboundPlayerDonateExperiencePacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public PacketType<ServerboundPlayerDonateExperiencePacket> type() {
        return dev.craftmine.network.CraftmineGamePacketTypes.SERVERBOUND_PLAYER_DONATE_EXPERIENCE;
    }

    @Override
    public void handle(ServerGamePacketListener serverGamePacketListener) {
        ((dev.craftmine.network.CraftmineServerPacketDuck)serverGamePacketListener).handlePlayerDonateExperience(this);
    }
}

