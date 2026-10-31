/*
 * Packet type constants for the craftmine additions (the fork added these to
 * GamePacketTypes; a mixin into GameProtocols registers the stream codecs).
 */
package dev.craftmine.network;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import dev.craftmine.network.packet.ClientboundChangeDimensionTypePacket;
import dev.craftmine.network.packet.ClientboundOpenDoorPacket;
import dev.craftmine.network.packet.ClientboundOpenWindowPacket;
import dev.craftmine.network.packet.ClientboundUpdatePlayerUnlocksPacket;
import dev.craftmine.network.packet.ClientboundUpdateScreenPacket;
import dev.craftmine.network.packet.ClientboundUpdateUnlockedEffectsPacket;
import dev.craftmine.network.packet.ServerboundMineCrafterRerollPacket;
import dev.craftmine.network.packet.ServerboundPlayerRelockUnlockPacket;
import dev.craftmine.network.packet.ServerboundPlayerBuyUnlockPacket;
import dev.craftmine.network.packet.ServerboundPlayerDonateExperiencePacket;
import dev.craftmine.network.packet.ServerboundPlayerReactivateUnlockPacket;
import net.minecraft.resources.Identifier;

public final class CraftmineGamePacketTypes {
    private CraftmineGamePacketTypes() {
    }

    public static final PacketType<ClientboundOpenWindowPacket> CLIENTBOUND_OPEN_WINDOW =
        new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.withDefaultNamespace("open_window"));
    public static final PacketType<ClientboundUpdateScreenPacket> CLIENTBOUND_UPDATE_SCREEN =
        new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.withDefaultNamespace("update_screen"));
    public static final PacketType<ClientboundUpdateUnlockedEffectsPacket> CLIENTBOUND_UPDATE_UNLOCKED_EFFECTS =
        new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.withDefaultNamespace("update_unlocked_effects"));
    public static final PacketType<ClientboundUpdatePlayerUnlocksPacket> CLIENTBOUND_UPDATE_PLAYER_UNLOCKS =
        new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.withDefaultNamespace("player_unlocks"));
    public static final PacketType<ClientboundChangeDimensionTypePacket> CLIENTBOUND_CHANGE_DIMENSION_TYPE =
        new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.withDefaultNamespace("change_dimension_type"));
    public static final PacketType<dev.craftmine.network.packet.ClientboundSetSkyPacket> CLIENTBOUND_SET_SKY =
        new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.withDefaultNamespace("set_sky"));
    public static final PacketType<ClientboundOpenDoorPacket> CLIENTBOUND_OPEN_DOOR_PACKET =
        new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.withDefaultNamespace("open_door_packet"));

    public static final PacketType<ServerboundPlayerDonateExperiencePacket> SERVERBOUND_PLAYER_DONATE_EXPERIENCE =
        new PacketType<>(PacketFlow.SERVERBOUND, Identifier.withDefaultNamespace("player_donate_experience"));
    public static final PacketType<ServerboundPlayerBuyUnlockPacket> SERVERBOUND_PLAYER_BUY_UNLOCK =
        new PacketType<>(PacketFlow.SERVERBOUND, Identifier.withDefaultNamespace("player_buy_unlock"));
    public static final PacketType<ServerboundPlayerRelockUnlockPacket> SERVERBOUND_PLAYER_RELOCK_UNLOCK =
        new PacketType<>(PacketFlow.SERVERBOUND, Identifier.withDefaultNamespace("player_relock_unlock"));
    public static final PacketType<ServerboundMineCrafterRerollPacket> SERVERBOUND_MINE_CRAFTER_REROLL =
        new PacketType<>(PacketFlow.SERVERBOUND, Identifier.withDefaultNamespace("mine_crafter_reroll"));
    public static final PacketType<ServerboundPlayerReactivateUnlockPacket> SERVERBOUND_PLAYER_REACTIVATE_UNLOCK =
        new PacketType<>(PacketFlow.SERVERBOUND, Identifier.withDefaultNamespace("player_reactivate_unlock"));
}
