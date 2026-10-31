package dev.craftmine.network.packet;

import dev.craftmine.registry.CraftmineRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import dev.craftmine.unlock.PlayerUnlock;

public record ServerboundPlayerReactivateUnlockPacket(Holder<PlayerUnlock> unlock) implements Packet<ServerGamePacketListener>
{
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlayerReactivateUnlockPacket> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.holderRegistry(CraftmineRegistries.PLAYER_UNLOCK_KEY), ServerboundPlayerReactivateUnlockPacket::unlock, ServerboundPlayerReactivateUnlockPacket::new);

    @Override
    public PacketType<ServerboundPlayerReactivateUnlockPacket> type() {
        return dev.craftmine.network.CraftmineGamePacketTypes.SERVERBOUND_PLAYER_REACTIVATE_UNLOCK;
    }

    @Override
    public void handle(ServerGamePacketListener serverGamePacketListener) {
        ((dev.craftmine.network.CraftmineServerPacketDuck)serverGamePacketListener).handleReactivateUnlock(this);
    }
}

