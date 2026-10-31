/*
 * Registers the craftmine packets into the 26.3 play-protocol templates.
 */
package dev.craftmine.mixin;

import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.ProtocolInfoBuilder;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import dev.craftmine.network.packet.ClientboundChangeDimensionTypePacket;
import dev.craftmine.network.packet.ClientboundOpenDoorPacket;
import dev.craftmine.network.packet.ClientboundOpenWindowPacket;
import dev.craftmine.network.packet.ClientboundUpdatePlayerUnlocksPacket;
import dev.craftmine.network.packet.ClientboundUpdateScreenPacket;
import dev.craftmine.network.packet.ClientboundUpdateUnlockedEffectsPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import dev.craftmine.network.packet.ServerboundMineCrafterRerollPacket;
import dev.craftmine.network.packet.ServerboundPlayerRelockUnlockPacket;
import dev.craftmine.network.packet.ServerboundPlayerBuyUnlockPacket;
import dev.craftmine.network.packet.ServerboundPlayerDonateExperiencePacket;
import dev.craftmine.network.packet.ServerboundPlayerReactivateUnlockPacket;
import dev.craftmine.network.CraftmineGamePacketTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameProtocols.class)
public abstract class GameProtocolsMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/ProtocolInfoBuilder;contextServerboundProtocol(Lnet/minecraft/network/ConnectionProtocol;Ljava/util/function/Consumer;)Lnet/minecraft/network/protocol/UnboundProtocol;"))
    private static net.minecraft.network.protocol.UnboundProtocol<ServerGamePacketListener, RegistryFriendlyByteBuf, GameProtocols.Context> craftmine$serverbound(net.minecraft.network.ConnectionProtocol protocol, Consumer<ProtocolInfoBuilder<ServerGamePacketListener, RegistryFriendlyByteBuf, GameProtocols.Context>> consumer) {
        return ProtocolInfoBuilder.contextServerboundProtocol(protocol, builder -> {
            consumer.accept(builder);
            builder.addPacket(CraftmineGamePacketTypes.SERVERBOUND_PLAYER_DONATE_EXPERIENCE, ServerboundPlayerDonateExperiencePacket.STREAM_CODEC);
            builder.addPacket(CraftmineGamePacketTypes.SERVERBOUND_PLAYER_BUY_UNLOCK, ServerboundPlayerBuyUnlockPacket.STREAM_CODEC);
            builder.addPacket(CraftmineGamePacketTypes.SERVERBOUND_PLAYER_REACTIVATE_UNLOCK, ServerboundPlayerReactivateUnlockPacket.STREAM_CODEC);
            builder.addPacket(CraftmineGamePacketTypes.SERVERBOUND_PLAYER_RELOCK_UNLOCK, ServerboundPlayerRelockUnlockPacket.STREAM_CODEC);
            builder.addPacket(CraftmineGamePacketTypes.SERVERBOUND_MINE_CRAFTER_REROLL, ServerboundMineCrafterRerollPacket.STREAM_CODEC);
        });
    }

    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/ProtocolInfoBuilder;clientboundProtocol(Lnet/minecraft/network/ConnectionProtocol;Ljava/util/function/Consumer;)Lnet/minecraft/network/protocol/SimpleUnboundProtocol;"))
    private static net.minecraft.network.protocol.SimpleUnboundProtocol<ClientGamePacketListener, RegistryFriendlyByteBuf> craftmine$clientbound(net.minecraft.network.ConnectionProtocol protocol, Consumer<ProtocolInfoBuilder<ClientGamePacketListener, RegistryFriendlyByteBuf, net.minecraft.util.Unit>> consumer) {
        return ProtocolInfoBuilder.clientboundProtocol(protocol, builder -> {
            consumer.accept(builder);
            builder.addPacket(CraftmineGamePacketTypes.CLIENTBOUND_OPEN_WINDOW, ClientboundOpenWindowPacket.STREAM_CODEC);
            builder.addPacket(CraftmineGamePacketTypes.CLIENTBOUND_UPDATE_SCREEN, ClientboundUpdateScreenPacket.STREAM_CODEC);
            builder.addPacket(CraftmineGamePacketTypes.CLIENTBOUND_UPDATE_UNLOCKED_EFFECTS, ClientboundUpdateUnlockedEffectsPacket.STREAM_CODEC);
            builder.addPacket(CraftmineGamePacketTypes.CLIENTBOUND_UPDATE_PLAYER_UNLOCKS, ClientboundUpdatePlayerUnlocksPacket.STREAM_CODEC);
            builder.addPacket(CraftmineGamePacketTypes.CLIENTBOUND_CHANGE_DIMENSION_TYPE, ClientboundChangeDimensionTypePacket.STREAM_CODEC);
            builder.addPacket(CraftmineGamePacketTypes.CLIENTBOUND_OPEN_DOOR_PACKET, ClientboundOpenDoorPacket.STREAM_CODEC);
            builder.addPacket(CraftmineGamePacketTypes.CLIENTBOUND_SET_SKY, dev.craftmine.network.packet.ClientboundSetSkyPacket.STREAM_CODEC);
        });
    }
}
