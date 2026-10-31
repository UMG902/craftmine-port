/**
 * Syncs which world effects are unlocked versus currently active on the
player's mine level. Handled by ClientPacketListenerMixin.handleUpdateUnlockedEffects.
 */
package dev.craftmine.network.packet;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import dev.craftmine.mines.WorldEffect;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

/**
 * Syncs the world effect state of a level to the client: the UNLOCKED
 * effects (refreshed whenever a new effect unlocks) and the ACTIVE effects
 * of the level the receiving player is currently in.
 *
 * <p>Fork original: CommonPlayerSpawnInfo carried both lists into
 * ClientLevel's constructor at every login/respawn (the active list only
 * ever changed with the level). 26.3 has no such spawn-info hook, so both
 * lists ride this packet instead - sent at sendLevelInfo time (login +
 * every level change, PlayerListMixin) and on unlock broadcasts
 * (ServerLevelMixin.unlockEffect, per player's CURRENT level so a hub
 * player never receives a mine's active effects).
 */
public record ClientboundUpdateUnlockedEffectsPacket(List<WorldEffect> unlockedEffects, List<WorldEffect> activeEffects) implements Packet<ClientGamePacketListener>
{
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateUnlockedEffectsPacket> STREAM_CODEC = Packet.codec(ClientboundUpdateUnlockedEffectsPacket::write, ClientboundUpdateUnlockedEffectsPacket::new);

    private ClientboundUpdateUnlockedEffectsPacket(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
        this(
            (List)WorldEffect.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(registryFriendlyByteBuf),
            (List)WorldEffect.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(registryFriendlyByteBuf)
        );
    }

    private void write(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
        WorldEffect.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(registryFriendlyByteBuf, this.unlockedEffects);
        WorldEffect.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(registryFriendlyByteBuf, this.activeEffects);
    }

    @Override
    public PacketType<ClientboundUpdateUnlockedEffectsPacket> type() {
        return dev.craftmine.network.CraftmineGamePacketTypes.CLIENTBOUND_UPDATE_UNLOCKED_EFFECTS;
    }

    @Override
    public void handle(ClientGamePacketListener clientGamePacketListener) {
        ((dev.craftmine.network.CraftmineClientPacketDuck)clientGamePacketListener).handleUpdateUnlockedEffects(this);
    }
}
