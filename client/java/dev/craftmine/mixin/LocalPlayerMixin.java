package dev.craftmine.mixin;

import dev.craftmine.duck.CraftmineClientUnlocksDuck;
import dev.craftmine.duck.CraftmineUnlockViewDuck;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import dev.craftmine.unlock.PlayerUnlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fork-faithful client unlock view: the fork's LocalPlayer.isActive/isUnlocked
 * delegate to connection.getUnlocks() (the synced ClientPlayerUnlocks); base
 * Player returns false. Backs CraftmineDuckBridge.isActive/isUnlocked for
 * shared code, so client paths (tooltips, mobInteract) stop CCE-ing on the
 * server-only CraftminePlayerDuck (task 49).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin implements CraftmineUnlockViewDuck {
    // NOTE: experienceLevel (Player) and level() (Entity) are inherited by
    // LocalPlayer, not declared on it. @Shadow only binds members declared on
    // the target class itself, so shadowing them aborts the launch with
    // "@Shadow method level() ... was not located in the target class".
    // Both are public, so the injector reads them through a cast instead.

    /**
     * Task 64: the fork's LocalPlayer.tick level-up popup (fork fields
     * lastXpPopupLevel / lastXpPopupTime, 1:1): whenever the local player's XP
     * level rises, show the LevelUpToast (throttled to once per 200 ticks so a
     * burst of XP does not spam toasts). The port already shipped the
     * LevelUpToast class - it was never wired, so level-ups showed nothing
     * (verified gap from the task-64 AI-claims audit). Runs in the same order
     * as the fork (right after super.tick() - TAIL here is equivalent: no
     * other tick code observes these fields).
     */
    @Unique
    private int craftmine$lastXpPopupLevel;
    @Unique
    private long craftmine$lastXpPopupTime;

    @Inject(method = "tick()V", at = @org.spongepowered.asm.mixin.injection.At("TAIL"))
    private void craftmine$levelUpToast(CallbackInfo ci) {
        net.minecraft.client.player.LocalPlayer self = (net.minecraft.client.player.LocalPlayer) (Object) this;
        int xpLevel = self.experienceLevel;
        if (this.craftmine$lastXpPopupLevel != xpLevel) {
            if (xpLevel > this.craftmine$lastXpPopupLevel) {
                long l = self.level().getGameTime();
                if (l > this.craftmine$lastXpPopupTime + 200L) {
                    net.minecraft.client.Minecraft.getInstance().gui.toastManager().addToast(new dev.craftmine.client.screen.LevelUpToast(xpLevel));
                    this.craftmine$lastXpPopupTime = l;
                }
            }
            this.craftmine$lastXpPopupLevel = xpLevel;
        }
    }

    @Shadow
    @Final
    public ClientPacketListener connection;

    @Override
    public boolean craftmine$isUnlocked(Holder<PlayerUnlock> holder) {
        return ((CraftmineClientUnlocksDuck) this.connection).craftmine$getUnlocks().isUnlocked(holder);
    }

    @Override
    public boolean craftmine$isActive(Holder<PlayerUnlock> holder) {
        return ((CraftmineClientUnlocksDuck) this.connection).craftmine$getUnlocks().isActive(holder);
    }
}
