package dev.craftmine.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ServerGamePacketListener;

/** Unoriginal feature: the minecrafter reroll button. The server validates unlocks and the open menu. */
public record ServerboundMineCrafterRerollPacket() implements Packet<ServerGamePacketListener>
{
    public static final ServerboundMineCrafterRerollPacket INSTANCE = new ServerboundMineCrafterRerollPacket();
    public static final StreamCodec<ByteBuf, ServerboundMineCrafterRerollPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public PacketType<ServerboundMineCrafterRerollPacket> type() {
        return dev.craftmine.network.CraftmineGamePacketTypes.SERVERBOUND_MINE_CRAFTER_REROLL;
    }

    @Override
    public void handle(ServerGamePacketListener serverGamePacketListener) {
        ((dev.craftmine.network.CraftmineServerPacketDuck)serverGamePacketListener).handleMineCrafterReroll(this);
    }
}
