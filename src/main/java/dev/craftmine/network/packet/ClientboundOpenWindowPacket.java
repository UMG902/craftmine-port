package dev.craftmine.network.packet;

import java.util.List;
import dev.craftmine.network.CraftmineClientPacketDuck;
import dev.craftmine.network.CraftmineGamePacketTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

/**
 * Opens a container screen on the client. Craftmine variant of the vanilla
 * open-window packet, extended with {@code additionalData}: an opaque integer
 * list the receiving menu can read back through CraftmineMenuDuck.updateData.
 * The Mine Crafter menu carries its rolled ingredient/resource keys this way.
 *
 * <p>Handled by ClientPacketListenerMixin.handleWindowScreen; the two custom
 * menu types (map making / dimension control) bypass MenuScreens because their
 * ScreenConstructors cannot be implemented outside the vanilla class.
 */
public record ClientboundOpenWindowPacket(int containerId, MenuType<?> menuType, Component title, List<Integer> additionalData)
	implements Packet<ClientGamePacketListener> {

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenWindowPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.CONTAINER_ID, ClientboundOpenWindowPacket::containerId,
		ByteBufCodecs.registry(Registries.MENU), ClientboundOpenWindowPacket::menuType,
		ComponentSerialization.TRUSTED_STREAM_CODEC, ClientboundOpenWindowPacket::title,
		ByteBufCodecs.INT.apply(ByteBufCodecs.list()), ClientboundOpenWindowPacket::additionalData,
		ClientboundOpenWindowPacket::new
	);

	@Override
	public PacketType<ClientboundOpenWindowPacket> type() {
		return CraftmineGamePacketTypes.CLIENTBOUND_OPEN_WINDOW;
	}

	@Override
	public void handle(ClientGamePacketListener listener) {
		((CraftmineClientPacketDuck) listener).handleWindowScreen(this);
	}
}
