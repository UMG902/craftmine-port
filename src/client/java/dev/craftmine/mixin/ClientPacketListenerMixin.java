/*
 * Client-side craftmine packet handling + the player-unlocks tracker.
 * Original: the fork extended ClientPacketListener inline.
 */
package dev.craftmine.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import dev.craftmine.unlock.ClientPlayerUnlocks;
import net.minecraft.client.gui.screens.MenuScreens;
import dev.craftmine.client.screen.DoorScreen;
import dev.craftmine.network.packet.ClientboundChangeDimensionTypePacket;
import dev.craftmine.network.packet.ClientboundSetSkyPacket;
import dev.craftmine.network.packet.ClientboundOpenDoorPacket;
import dev.craftmine.network.packet.ClientboundOpenWindowPacket;
import dev.craftmine.network.packet.ClientboundUpdatePlayerUnlocksPacket;
import dev.craftmine.network.packet.ClientboundUpdateScreenPacket;
import dev.craftmine.network.packet.ClientboundUpdateUnlockedEffectsPacket;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;

import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.craftmine.Craftmine;
import dev.craftmine.network.CraftmineClientPacketDuck;
import dev.craftmine.registry.CraftmineRegistries;
import dev.craftmine.duck.CraftmineClientLevelDuck;
import dev.craftmine.server.SkyDefaults;
import dev.craftmine.duck.CraftmineClientUnlocksDuck;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.duck.CraftmineMenuDuck;
import dev.craftmine.registry.CraftmineContent;
import net.minecraft.client.gui.screens.Screen;
import dev.craftmine.client.screen.DimensionControlScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import dev.craftmine.client.screen.MineCraftingScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import dev.craftmine.inventory.DimensionControlMenu;
import dev.craftmine.inventory.MineCraftingMenu;
import net.minecraft.network.chat.Component;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin implements CraftmineClientPacketDuck, CraftmineClientUnlocksDuck {
    // `minecraft` is declared on the parent ClientCommonPacketListenerImpl;
    // @Shadow only resolves members declared in the target class, so read it
    // through the accessor mixin instead.
    @Unique
    private Minecraft craftmine$minecraft() {
        return ((ClientCommonPacketListenerImplAccessor) (Object) this).craftmine$minecraft();
    }

    @Unique
    private ClientPlayerUnlocks craftmine$unlocks = new ClientPlayerUnlocks();

    @Inject(method = "<init>", at = @At("HEAD"))
    private static void craftmine$fillClientRegistries(CallbackInfo ci) {
        // STATIC is mandatory: @At("HEAD") on a <init> targets the point BEFORE
        // the super() call, and Mixin rejects instance handlers there
        // ("handler before super() invocation must be static"). The body only
        // touches static state, so nothing else changes.
        // Fill the craftmine-only registries (player_unlock, world_effect, ...)
        // on the CLIENT side, BEFORE the TAIL hook below constructs
        // ClientPlayerUnlocks (its tree is built from PLAYER_UNLOCK contents).
        // - integrated server: ReloadableServerResourcesMixin already filled
        //   them during world creation, so this is an idempotent no-op;
        // - pure client (dedicated-server play): nothing else ever runs the
        //   fill, leaving PLAYER_UNLOCK empty -> empty unlocks screen.
        // Safe here: vanilla bootstrap + freeze have long finished (component
        // map bound), and the registry statics only build vanilla-item stacks.
        try {
            dev.craftmine.registry.CraftmineBootstrap.bootstrapCustomRegistries();
        } catch (Throwable t) {
            Craftmine.LOGGER.error("Craftmine: failed to fill craftmine registries on client", t);
        }
        Craftmine.LOGGER.info("Craftmine: client registries ready (player_unlock entries: {}, world_effect entries: {})",
            CraftmineRegistries.PLAYER_UNLOCK.keySet().size(), CraftmineRegistries.WORLD_EFFECT.keySet().size());
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$initFields(CallbackInfo ci) {
        // Mixin does not merge field initializers on this class-file version.
        if (this.craftmine$unlocks == null) this.craftmine$unlocks = new ClientPlayerUnlocks();
        Craftmine.LOGGER.info("Craftmine: client unlocks tracker created");
    }

    @Unique
    @Override
    public ClientPlayerUnlocks craftmine$getUnlocks() {
        return this.craftmine$unlocks;
    }

    @Unique
    @Override
    public void handleWindowScreen(ClientboundOpenWindowPacket clientboundOpenWindowPacket) {
        Minecraft minecraft = craftmine$minecraft();
        PacketUtils.ensureRunningOnSameThread(clientboundOpenWindowPacket, (ClientPacketListener) (Object) this, minecraft.packetProcessor());
        MenuType<?> menuType = clientboundOpenWindowPacket.menuType();
        if (menuType == CraftmineContent.MAP_MAKING || menuType == CraftmineContent.DIMENSION_CONTROL_MENU) {
            // The craftmine menus cannot go through MenuScreens.create: its
            // ScreenConstructor is a private inner interface that no outside
            // class can implement (the 25w14craftmine snapshot registers them
            // inside MenuScreens' own static init). Replicate the exact
            // ScreenConstructor.fromPacket body instead.
            craftmine$openMenu(menuType, minecraft, clientboundOpenWindowPacket.containerId(), clientboundOpenWindowPacket.title(), clientboundOpenWindowPacket.additionalData());
        } else {
            MenuScreens.create(menuType, minecraft, clientboundOpenWindowPacket.containerId(), clientboundOpenWindowPacket.title());
        }
        AbstractContainerMenu menu = minecraft.player != null ? minecraft.player.containerMenu : null;
        if (menu != null && menu.getType() == clientboundOpenWindowPacket.menuType()) {
            ((CraftmineMenuDuck) menu).updateData(clientboundOpenWindowPacket.additionalData());
        }
    }

    @Unique
    private static <T extends AbstractContainerMenu> void craftmine$openMenu(MenuType<T> menuType, Minecraft minecraft, int containerId, Component title, java.util.List<Integer> additionalData) {
        // The fork's MenuScreens ScreenConstructor.fromPacket constructs the menu
        // WITH the packet's additional data. MenuType.create(2-arg) has no data
        // parameter - passing List.of() would crash MineCraftingMenu's
        // setAdditionalData (list.get(0)) - so build each menu directly.
        Inventory inventory = minecraft.player.getInventory();
        AbstractContainerMenu menu;
        Screen screen;
        if (menuType == CraftmineContent.MAP_MAKING) {
            MineCraftingMenu mineCraftingMenu = new MineCraftingMenu(containerId, inventory, additionalData);
            menu = mineCraftingMenu;
            screen = new MineCraftingScreen(mineCraftingMenu, inventory, title);
        } else if (menuType == CraftmineContent.DIMENSION_CONTROL_MENU) {
            DimensionControlMenu dimensionControlMenu = new DimensionControlMenu(containerId, inventory, additionalData);
            menu = dimensionControlMenu;
            screen = new DimensionControlScreen(dimensionControlMenu, inventory, title);
        } else {
            throw new IllegalStateException("Unregistered craftmine menu type: " + BuiltInRegistries.MENU.getKey(menuType));
        }
        minecraft.player.containerMenu = ((MenuAccess) screen).getMenu();
        minecraft.gui.setScreen(screen);
    }

    @Unique
    @Override
    public void handleUpdateScreen(ClientboundUpdateScreenPacket clientboundUpdateScreenPacket) {
        Minecraft minecraft = craftmine$minecraft();
        PacketUtils.ensureRunningOnSameThread(clientboundUpdateScreenPacket, (ClientPacketListener) (Object) this, minecraft.packetProcessor());
        AbstractContainerMenu abstractContainerMenu = minecraft.player.containerMenu;
        if (abstractContainerMenu.getType() == clientboundUpdateScreenPacket.menuType()) {
            ((CraftmineMenuDuck) abstractContainerMenu).updateData(clientboundUpdateScreenPacket.additionalData());
        }
    }

    @Unique
    @Override
    public void handleUpdateUnlockedEffects(ClientboundUpdateUnlockedEffectsPacket clientboundUpdateUnlockedEffectsPacket) {
        Minecraft minecraft = craftmine$minecraft();
        PacketUtils.ensureRunningOnSameThread(clientboundUpdateUnlockedEffectsPacket, (ClientPacketListener) (Object) this, minecraft.packetProcessor());
        ClientLevel level = minecraft.level;
        if (level != null) {
            ((CraftmineClientLevelDuck) (Object) level).setUnlockedWorldEffects(clientboundUpdateUnlockedEffectsPacket.unlockedEffects());
            ((CraftmineClientLevelDuck) (Object) level).setActiveWorldEffects(clientboundUpdateUnlockedEffectsPacket.activeEffects());
        }
    }

    @Unique
    @Override
    public void handleUpdatePlayerUnlocksPacket(ClientboundUpdatePlayerUnlocksPacket clientboundUpdatePlayerUnlocksPacket) {
        Minecraft minecraft = craftmine$minecraft();
        PacketUtils.ensureRunningOnSameThread(clientboundUpdatePlayerUnlocksPacket, (ClientPacketListener) (Object) this, minecraft.packetProcessor());
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(clientboundUpdatePlayerUnlocksPacket.id());
        if (entity == minecraft.player) {
            this.craftmine$unlocks.update(clientboundUpdatePlayerUnlocksPacket);
        }
        // RemotePlayer unlock mirroring is cosmetic and not ported (26.3 render
        // states have no unlock state); the local player tracker is authoritative.
    }

    @Unique
    @Override
    public void handleOpenDoorPacket(ClientboundOpenDoorPacket clientboundOpenDoorPacket) {
        Minecraft minecraft = craftmine$minecraft();
        PacketUtils.ensureRunningOnSameThread(clientboundOpenDoorPacket, (ClientPacketListener) (Object) this, minecraft.packetProcessor());
        DoorScreen doorScreen = new DoorScreen(clientboundOpenDoorPacket.containerId(), minecraft.player.getInventory(), clientboundOpenDoorPacket.options());
        minecraft.player.containerMenu = doorScreen.getMenu();
        minecraft.setScreenAndShow(doorScreen);
    }

    @Unique
    @Override
    public void handleChangeDimensionType(ClientboundChangeDimensionTypePacket clientboundChangeDimensionTypePacket) {
        Minecraft minecraft = craftmine$minecraft();
        PacketUtils.ensureRunningOnSameThread(clientboundChangeDimensionTypePacket, (ClientPacketListener) (Object) this, minecraft.packetProcessor());
        ClientLevel level = minecraft.level;
        if (level != null) {
            ((CraftmineLevelDuck) (Object) level).craftmine$setDimensionType(clientboundChangeDimensionTypePacket.dimensionType());
        }
    }

    @Unique
    @Override
    public void handleSetSky(ClientboundSetSkyPacket clientboundSetSkyPacket) {
        Minecraft minecraft = craftmine$minecraft();
        PacketUtils.ensureRunningOnSameThread(clientboundSetSkyPacket, (ClientPacketListener) (Object) this, minecraft.packetProcessor());
        ClientLevel level = minecraft.level;
        if (level != null && clientboundSetSkyPacket.dimension().equals(level.dimension().identifier())) {
            ((CraftmineLevelDuck) (Object) level).craftmine$setActiveSky(clientboundSetSkyPacket.sky().orElse(null));
        }
    }

    /**
     * Craftmine SkyWindows: a full chunk arrived (fresh or replaced) - drop
     * any cached window geometry for it so it gets rescanned (the scan finds
     * the sky/door/dimension-control blocks the chunk now contains).
     */
    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void craftmine$skyWindowChunkLoaded(net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        dev.craftmine.client.render.SkyWindowRenderer.get().onChunkReload(packet.x(), packet.z());
    }

    /**
     * Craftmine SkyWindows: the chunk is going away - drop its cached window
     * geometry (and its GPU buffer).
     */
    @Inject(method = "handleForgetLevelChunk", at = @At("TAIL"))
    private void craftmine$skyWindowChunkForgotten(net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        dev.craftmine.client.render.SkyWindowRenderer.get().onChunkReload(packet.pos().x(), packet.pos().z());
    }
}
