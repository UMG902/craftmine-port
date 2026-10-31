package dev.craftmine.server;

import java.util.List;
import java.util.OptionalInt;

import net.minecraft.util.Util;
import net.minecraft.server.MinecraftServer;
import dev.craftmine.server.TheGame;
import net.minecraft.server.level.ServerPlayer;
import dev.craftmine.network.packet.ClientboundOpenWindowPacket;
import dev.craftmine.duck.CraftmineMenuDuck;
import dev.craftmine.mixin.ServerPlayerAccessor;

/**
 * Static bridge between ported craftmine classes and the vanilla 26.3 MinecraftServer.
 * The original fork put these members directly on MinecraftServer; here they live in one
 * place and are wired to the server instance via mixins.
 */
public final class CraftmineServerHooks {
    private CraftmineServerHooks() {
    }

    /**
     * Opens a craftmine menu the way the 25w14craftmine fork does: identical to
     * vanilla ServerPlayer.openMenu, but sends the fork's
     * ClientboundOpenWindowPacket (carries additionalData) instead of the
     * vanilla ClientboundOpenScreenPacket.
     *
     * Why: the fork's menus (map_making / dimension_control) are not registered
     * in 26.3's MenuScreens (its ScreenConstructor is a private inner
     * interface), so a vanilla open-screen packet is silently dropped by the
     * client - right-clicking the mine crafter did nothing. The client routes
     * ClientboundOpenWindowPacket to craftmine screens via
     * ClientPacketListenerMixin.handleWindowScreen, which also feeds the
     * additional data into the menu (CraftmineMenuDuck.updateData).
     */
    public static OptionalInt openCraftmineMenu(ServerPlayer player, net.minecraft.world.MenuProvider menuProvider) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        ServerPlayerAccessor accessor = (ServerPlayerAccessor) player;
        accessor.craftmine$nextContainerCounter();
        int containerId = accessor.craftmine$getContainerCounter();
        net.minecraft.world.inventory.AbstractContainerMenu menu =
            menuProvider.createMenu(containerId, player.getInventory(), player);
        if (menu == null) {
            return OptionalInt.empty();
        }
        List<Integer> additionalData = menu instanceof CraftmineMenuDuck duck
            ? duck.craftmine$getAdditionalData()
            : List.of();
        player.connection.send(new ClientboundOpenWindowPacket(containerId, menu.getType(), menuProvider.getDisplayName(), additionalData));
        player.containerMenu = menu;
        accessor.craftmine$initMenu(menu);
        return OptionalInt.of(containerId);
    }

    /** Mirrors craftmine's MinecraftServer.theGame. */
    private static TheGame theGame;

    /** Mirrors craftmine's MinecraftServer.theGameButSuperSpecialOneForLoading. */
    public static TheGame theGameButSuperSpecialOneForLoading;

    public static TheGame theGame() {
        return theGame;
    }

    public static void setTheGame(TheGame game) {
        theGame = game;
    }

    /** craftmine's MinecraftServer.runNextTickNow(): schedule the next tick immediately. */
    public static void runNextTickNow(MinecraftServer server) {
        server.nextTickTimeNanos = Util.getNanos();
    }

    /** craftmine's MinecraftServer.runTicksDuringServerPrepare(). */
    public static void runTicksDuringServerPrepare(MinecraftServer server) {
        server.nextTickTimeNanos = Util.getNanos() + MinecraftServer.PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
        server.waitUntilNextTick();
    }

    /**
     * craftmine's MinecraftServer.sayGoodbye(): move all players into
     * configuration state. In the original this was the WorldReloader
     * hand-off — players left play, the world reloaded, players came back.
     * Here the resync machine drives the 26.3 configuration phase (registry
     * sync + rejoin) and the future completes once every client is back in
     * the play phase, so callers can teleport right after.
     */
    public static java.util.concurrent.CompletableFuture<MinecraftServer> sayGoodbye(MinecraftServer server) {
        java.util.concurrent.CompletableFuture<MinecraftServer> future = new java.util.concurrent.CompletableFuture<>();
        dev.craftmine.server.CraftmineClientResync.runWhenSynced(server, () -> future.complete(server));
        return future;
    }

    /** Port of craftmine's AdvancementType.createPlayerUnlockAnnouncement. */
    public static net.minecraft.network.chat.Component createPlayerUnlockAnnouncement(net.minecraft.core.Holder<dev.craftmine.unlock.PlayerUnlock> holder, net.minecraft.server.level.ServerPlayer serverPlayer) {
        net.minecraft.advancements.DisplayInfo displayInfo = holder.value().display();
        boolean bl = holder.value().defaultVisibility() == dev.craftmine.unlock.PlayerUnlock.UnlockVisibility.VISIBLE;
        net.minecraft.network.chat.Component component = bl ? displayInfo.description() : net.minecraft.network.chat.Component.translatable("unlocks.player.hidden");
        net.minecraft.network.chat.MutableComponent mutableComponent = net.minecraft.network.chat.ComponentUtils.wrapInSquareBrackets(displayInfo.title()).withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN).withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(component)));
        net.minecraft.network.chat.MutableComponent mutableComponent2 = net.minecraft.network.chat.ComponentUtils.mergeStyles(serverPlayer.getDisplayName().copy(), net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.ChatFormatting.WHITE));
        return net.minecraft.network.chat.Component.translatable("unlocks.player.unlocked", mutableComponent2, mutableComponent).withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    /** craftmine's PlayerList.syncContainerMenuData. */
    public static void syncContainerMenuData(net.minecraft.world.inventory.MenuType<?> menuType, java.util.List<Integer> list) {
        TheGame game = theGame;
        if (game == null) {
            return;
        }
        for (net.minecraft.server.level.ServerPlayer player : game.playerList().getPlayers()) {
            // Fork compares getTypeRaw() (null-safe): InventoryMenu/DoorMenu carry no
            // registered MenuType and vanilla getType() throws on them - that killed
            // the server tick whenever the mine crafter dispensed rewards while a
            // player was outside a registered menu (task 50 crash, 2026-10-20 16:40).
            if (((dev.craftmine.duck.CraftmineMenuTypeDuck) player.containerMenu).craftmine$getTypeRaw() != menuType) continue;
            ((dev.craftmine.duck.CraftmineMenuDuck) player.containerMenu).updateData(list);
            player.connection.send(new dev.craftmine.network.packet.ClientboundUpdateScreenPacket(menuType, list));
        }
    }
}
