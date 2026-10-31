/*
 * Implements the craftmine serverbound packet handlers on the server
 * game packet listener, and suppresses the vanilla "X left the game"
 * broadcast during a registry resync (the resync's config-phase hand-off
 * disconnects/rejoins every stale client - task 30: the entry must not
 * look like a logout in chat).
 */
package dev.craftmine.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import dev.craftmine.network.packet.ServerboundMineCrafterRerollPacket;
import dev.craftmine.network.packet.ServerboundPlayerRelockUnlockPacket;
import dev.craftmine.network.packet.ServerboundPlayerBuyUnlockPacket;
import dev.craftmine.network.packet.ServerboundPlayerDonateExperiencePacket;
import dev.craftmine.network.packet.ServerboundPlayerReactivateUnlockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.network.CraftmineServerPacketDuck;
import dev.craftmine.server.CraftmineClientResync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin implements CraftmineServerPacketDuck {
    @Shadow
    public abstract ServerPlayer getPlayer();

    /**
     * Task 32 fix: the play tick must not run while the resync machine has this
     * connection switched into the configuration phase. tick() has no
     * waitingForSwitchToConfig guard in 26.3: tickPlayer -> ServerPlayer.doTick
     * re-sends health/experience (dirty after the pre-switch mine teleport's
     * resetSentInfo) and those sends land BEHIND switchToConfig's in-band
     * outbound-protocol swap -> "EncoderException: Sending unknown packet" ->
     * disconnect. See CraftmineClientResync.isPlayTickFrozen.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void craftmine$freezePlayTickDuringResync(CallbackInfo ci) {
        Connection connection = ((ServerCommonPacketListenerAccessor) this).craftmine$getConnection();
        if (CraftmineClientResync.isPlayTickFrozen(connection)) {
            ci.cancel();
        }
    }

    /**
     * removePlayerFromWorld broadcasts "multiplayer.player.left" before saving
     * and removing the player. While the resync machine is active this is the
     * FAKE disconnect of the config-phase hand-off - drop the broadcast (the
     * matching "joined the game" suppression lives in PlayerListMixin).
     */
    @Redirect(
        method = "removePlayerFromWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
        )
    )
    private void craftmine$suppressResyncLeaveBroadcast(PlayerList playerList, Component message, boolean overlay) {
        if (!CraftmineClientResync.isActive()) {
            playerList.broadcastSystemMessage(message, overlay);
        }
    }

    @Override
    public void handlePlayerDonateExperience(ServerboundPlayerDonateExperiencePacket packet) {
        ServerPlayer player = this.getPlayer();
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.level());
        ((CraftminePlayerDuck)(Object)player).donateExperienceToMineCrafter();
    }

    @Override
    public void handleBuyUnlock(ServerboundPlayerBuyUnlockPacket packet) {
        ServerPlayer player = this.getPlayer();
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.level());
        ((CraftminePlayerDuck)(Object)player).buyUnlock(packet.unlock());
    }

    @Override
    public void handleReactivateUnlock(ServerboundPlayerReactivateUnlockPacket packet) {
        ServerPlayer player = this.getPlayer();
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.level());
        ((CraftminePlayerDuck)(Object)player).reactivateUnlock(packet.unlock());
    }

    @Override
    public void handleRelockUnlock(ServerboundPlayerRelockUnlockPacket packet) {
        ServerPlayer player = this.getPlayer();
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.level());
        ((CraftminePlayerDuck)(Object)player).relockUnlock(packet.unlock());
    }

    @Override
    public void handleMineCrafterReroll(ServerboundMineCrafterRerollPacket packet) {
        ServerPlayer player = this.getPlayer();
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.level());
        if (player.containerMenu instanceof dev.craftmine.inventory.MineCraftingMenu mineCraftingMenu) {
            mineCraftingMenu.rerollIngredients(player);
        }
    }
}
