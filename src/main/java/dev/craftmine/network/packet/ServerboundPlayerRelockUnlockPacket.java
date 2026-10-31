package dev.craftmine.network.packet;

import dev.craftmine.registry.CraftmineRegistries;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import dev.craftmine.unlock.PlayerUnlock;
import net.minecraft.network.protocol.game.ServerGamePacketListener;

/** Unoriginal feature: ctrl+click an unlock in the hub to relock it. The server validates everything. */
public record ServerboundPlayerRelockUnlockPacket(Holder<PlayerUnlock> unlock) implements Packet<ServerGamePacketListener>
{
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlayerRelockUnlockPacket> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.holderRegistry(CraftmineRegistries.PLAYER_UNLOCK_KEY), ServerboundPlayerRelockUnlockPacket::unlock, ServerboundPlayerRelockUnlockPacket::new);

    @Override
    public PacketType<ServerboundPlayerRelockUnlockPacket> type() {
        return dev.craftmine.network.CraftmineGamePacketTypes.SERVERBOUND_PLAYER_RELOCK_UNLOCK;
    }

    @Override
    public void handle(ServerGamePacketListener serverGamePacketListener) {
        ((dev.craftmine.network.CraftmineServerPacketDuck)serverGamePacketListener).handleRelockUnlock(this);
    }
}
