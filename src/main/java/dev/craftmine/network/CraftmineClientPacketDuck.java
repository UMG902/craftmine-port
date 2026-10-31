/*
 * Client-side handlers for the craftmine packets; implemented by a
 * ClientPacketListener mixin (client source set).
 */
package dev.craftmine.network;

import dev.craftmine.network.packet.ClientboundChangeDimensionTypePacket;
import dev.craftmine.network.packet.ClientboundOpenDoorPacket;
import dev.craftmine.network.packet.ClientboundOpenWindowPacket;
import dev.craftmine.network.packet.ClientboundUpdatePlayerUnlocksPacket;
import dev.craftmine.network.packet.ClientboundUpdateScreenPacket;
import dev.craftmine.network.packet.ClientboundUpdateUnlockedEffectsPacket;

public interface CraftmineClientPacketDuck {
    void handleWindowScreen(ClientboundOpenWindowPacket packet);

    void handleUpdateScreen(ClientboundUpdateScreenPacket packet);

    void handleUpdateUnlockedEffects(ClientboundUpdateUnlockedEffectsPacket packet);

    void handleUpdatePlayerUnlocksPacket(ClientboundUpdatePlayerUnlocksPacket packet);

    void handleOpenDoorPacket(ClientboundOpenDoorPacket packet);

    void handleChangeDimensionType(ClientboundChangeDimensionTypePacket packet);

    void handleSetSky(dev.craftmine.network.packet.ClientboundSetSkyPacket packet);
}
