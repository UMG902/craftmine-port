/*
 * PlayerList hooks for the craftmine port:
 *  1. sendLevelInfo TAIL - re-sync the level's active sky on every
 *     join/respawn/dimension change (the fork's set_sky packet; without
 *     this a re-join into a level with a control-override or default sky
 *     falls back to the client's stale guess). Task 30/32.
 *  2. placeNewPlayer - suppress the "joined the game" broadcast while the
 *     mine-entry resync is active (the fake disconnect would broadcast a
 *     join/leave pair every mine trip). Task 30. The "left" counterpart
 *     lives in ServerGamePacketListenerMixin.removePlayerFromWorld.
 *  3. save TAIL - fork-faithful unlock persistence: the original snapshot's
 *     PlayerList (awx decompile) writes the player's unlock JSON (amv.a)
     * on every player save. Task 35 (unlocks-reset fix, belt-and-braces on
     * top of save-on-mutation in ServerPlayerUnlocks).
 */
package dev.craftmine.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "sendLevelInfo", at = @At("TAIL"))
    private void craftmine$syncSky(ServerPlayer serverPlayer, ServerLevel serverLevel, CallbackInfo ci) {
        if (dev.craftmine.server.CraftmineServerHooks.theGame() == null) {
            return;
        }
        // Task 44: a STORED dimension-control override must also swap the
        // dimension type the client renders with (the visual environment:
        // attributes/timelines/clock), not just the sky value - otherwise a
        // re-join into a level with an overworld/end pick renders the
        // attribute defaults (black sky, frozen sun). SkyDefaults defaults
        // are NOT swapped: untouched levels keep their own type.
        java.util.Optional<dev.craftmine.level.DimensionSpecialEffects.Sky> stored =
            serverLevel.getDataStorage().computeIfAbsent(dev.craftmine.server.SkyData.TYPE).activeSky();
        dev.craftmine.level.DimensionSpecialEffects.Sky activeSky = stored.orElse(null);
        if (activeSky != null) {
            net.minecraft.world.level.dimension.DimensionType swapped = dev.craftmine.inventory.DimensionControlMenu.withSkyGate(serverLevel, activeSky);
            serverPlayer.connection.send(new dev.craftmine.network.packet.ClientboundChangeDimensionTypePacket(
                net.minecraft.core.Holder.direct(swapped)));
        }
        dev.craftmine.level.DimensionSpecialEffects.Sky sky =
            ((dev.craftmine.duck.CraftmineLevelDuck) (Object) serverLevel).craftmine$getActiveSky();
        serverPlayer.connection.send(new dev.craftmine.network.packet.ClientboundSetSkyPacket(
            serverLevel.dimension().identifier(), java.util.Optional.ofNullable(sky)));
        // Fork spawn info carried the level's unlocked + ACTIVE world effects
        // into ClientLevel's constructor (25w14craftmine CommonPlayerSpawnInfo,
        // applied at every login/respawn). 26.3 has no spawn-info hook, so both
        // lists ride ClientboundUpdateUnlockedEffectsPacket here - same moments
        // as the fork (sendLevelInfo = login + every level change), guaranteed
        // to be processed after the login/respawn packet created the new
        // ClientLevel. This is the mine-effects HUD's data source.
        serverPlayer.connection.send(new dev.craftmine.network.packet.ClientboundUpdateUnlockedEffectsPacket(
            ((dev.craftmine.duck.CraftmineLevelDuck) (Object) serverLevel).getUnlockedEffects(),
            ((dev.craftmine.duck.CraftmineLevelDuck) (Object) serverLevel).getActiveEffects()));
    }

    @Redirect(
        method = "placeNewPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
        )
    )
    private void craftmine$suppressJoinMessage(PlayerList playerList, Component message, boolean overlay) {
        if (!dev.craftmine.server.CraftmineClientResync.isActive()) {
            playerList.broadcastSystemMessage(message, overlay);
        }
    }

    @Inject(method = "save", at = @At("TAIL"))
    private void craftmine$saveUnlocks(ServerPlayer serverPlayer, CallbackInfo ci) {
        if (serverPlayer instanceof dev.craftmine.duck.CraftminePlayerDuck duck) {
            duck.craftmine$playerUnlocks().save();
        }
    }
}
