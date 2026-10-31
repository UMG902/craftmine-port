/*
 * Server-side handlers for the craftmine packets; implemented by a
 * ServerGamePacketListenerImpl mixin.
 */
package dev.craftmine.network;

import dev.craftmine.network.packet.ServerboundMineCrafterRerollPacket;
import dev.craftmine.network.packet.ServerboundPlayerRelockUnlockPacket;
import dev.craftmine.network.packet.ServerboundPlayerBuyUnlockPacket;
import dev.craftmine.network.packet.ServerboundPlayerDonateExperiencePacket;
import dev.craftmine.network.packet.ServerboundPlayerReactivateUnlockPacket;

public interface CraftmineServerPacketDuck {
    void handlePlayerDonateExperience(ServerboundPlayerDonateExperiencePacket packet);

    void handleBuyUnlock(ServerboundPlayerBuyUnlockPacket packet);

    void handleReactivateUnlock(ServerboundPlayerReactivateUnlockPacket packet);

    void handleRelockUnlock(ServerboundPlayerRelockUnlockPacket packet);

    void handleMineCrafterReroll(ServerboundMineCrafterRerollPacket packet);
}
