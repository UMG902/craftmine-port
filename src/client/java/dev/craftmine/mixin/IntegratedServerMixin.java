package dev.craftmine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;

/**
 * IntegratedServer twin of DedicatedServerMixin: the client's built-in server
 * boots through its own initServer() override, so TheGame adoption must hook
 * here too. Without it CraftmineServerHooks.theGame() stays null on single
 * player and the first player tick crashes in ServerPlayerUnlocks.flushDirty
 * (NPE: "TheGame.playerList()" via theGame() == null).
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {

    @Inject(method = "initServer", at = @At("TAIL"))
    private void craftmine$adoptTheGame(CallbackInfoReturnable<Boolean> cir) {
        dev.craftmine.server.CraftmineServerHooks.setTheGame(dev.craftmine.server.TheGame.adopt((MinecraftServer) (Object) this));
    }
}
