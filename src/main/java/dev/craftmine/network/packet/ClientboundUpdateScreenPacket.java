package dev.craftmine.network.packet;

import java.util.List;
import dev.craftmine.network.CraftmineClientPacketDuck;
import dev.craftmine.network.CraftmineGamePacketTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

/**
 * Pushes new {@code additionalData} into the currently open container menu
 * (identified by its MenuType) without re-opening the screen. Used while a
 * craftmine menu is open and its server-side state changes underneath it.
 *
 * <p>Handled by ClientPacketListenerMixin.handleUpdateScreen, which forwards
 * the payload through CraftmineMenuDuck.updateData.
 */
public record ClientboundUpdateScreenPacket(MenuType<?> menuType, List<Integer> additionalData)
	implements Packet<ClientGamePacketListener> {

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateScreenPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.registry(Registries.MENU), ClientboundUpdateScreenPacket::menuType,
		ByteBufCodecs.INT.apply(ByteBufCodecs.list()), ClientboundUpdateScreenPacket::additionalData,
		ClientboundUpdateScreenPacket::new
	);

	@Override
	public PacketType<ClientboundUpdateScreenPacket> type() {
		return CraftmineGamePacketTypes.CLIENTBOUND_UPDATE_SCREEN;
	}

	@Override
	public void handle(ClientGamePacketListener listener) {
		((CraftmineClientPacketDuck) listener).handleUpdateScreen(this);
	}
}
