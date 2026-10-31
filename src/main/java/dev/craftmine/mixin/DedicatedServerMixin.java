package dev.craftmine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * The original fork created TheGame inside DedicatedServer.initServer()
 * (the fork's ServerLevel ctor took a TheGame). 26.3 boots the world itself,
 * so we adopt the live server into a TheGame right after vanilla's
 * initServer() has created the levels and player list.
 */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {

    @Inject(method = "initServer", at = @At("TAIL"))
    private void craftmine$adoptTheGame(CallbackInfoReturnable<Boolean> cir) {
        dev.craftmine.server.CraftmineServerHooks.setTheGame(dev.craftmine.server.TheGame.adopt((MinecraftServer) (Object) this));
    }
}
