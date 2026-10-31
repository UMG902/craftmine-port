package dev.craftmine.network.packet;

import dev.craftmine.registry.CraftmineRegistries;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GamePacketTypes;
import dev.craftmine.unlock.PlayerUnlock;

public record ClientboundUpdatePlayerUnlocksPacket(int id, boolean reset, Map<Holder<PlayerUnlock>, Boolean> isActiveExclusive, Map<Holder<PlayerUnlock>, PlayerUnlock.UnlockVisibility> visibility, Map<Holder<PlayerUnlock>, Boolean> obtained) implements Packet<ClientGamePacketListener>
{
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdatePlayerUnlocksPacket> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ClientboundUpdatePlayerUnlocksPacket::id, ByteBufCodecs.BOOL, ClientboundUpdatePlayerUnlocksPacket::reset, ByteBufCodecs.map(HashMap::new, ByteBufCodecs.holderRegistry(CraftmineRegistries.PLAYER_UNLOCK_KEY), ByteBufCodecs.BOOL), ClientboundUpdatePlayerUnlocksPacket::isActiveExclusive, ByteBufCodecs.map(HashMap::new, ByteBufCodecs.holderRegistry(CraftmineRegistries.PLAYER_UNLOCK_KEY), PlayerUnlock.UnlockVisibility.STREAM_CODEC), ClientboundUpdatePlayerUnlocksPacket::visibility, ByteBufCodecs.map(HashMap::new, ByteBufCodecs.holderRegistry(CraftmineRegistries.PLAYER_UNLOCK_KEY), ByteBufCodecs.BOOL), ClientboundUpdatePlayerUnlocksPacket::obtained, ClientboundUpdatePlayerUnlocksPacket::new);

    @Override
    public PacketType<ClientboundUpdatePlayerUnlocksPacket> type() {
        return dev.craftmine.network.CraftmineGamePacketTypes.CLIENTBOUND_UPDATE_PLAYER_UNLOCKS;
    }

    @Override
    public void handle(ClientGamePacketListener clientGamePacketListener) {
        ((dev.craftmine.network.CraftmineClientPacketDuck)clientGamePacketListener).handleUpdatePlayerUnlocksPacket(this);
    }
}

