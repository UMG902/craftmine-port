/*
 * Mixin: craftmine additions to PlayerAdvancements (ports the
 * 25w14craftmine UnlockCondition.onPlayerAdvancement dispatch).
 *
 * Fork call site: PlayerAdvancements.award, right before
 * AdvancementRewards.grant (i.e. only when the advancement just COMPLETED).
 * 26.3 moved the class to net.minecraft.server. This is how advancement-gated
 * ingredients (e.g. SpecialMines' return_to_sender) unlocked.
 */
package dev.craftmine.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import dev.craftmine.unlock.UnlockCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    protected ServerPlayer player;

    @Inject(method = "award", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V", shift = At.Shift.BEFORE))
    private void craftmine$onPlayerAdvancement(AdvancementHolder advancementHolder, String string, CallbackInfoReturnable<Boolean> cir) {
        UnlockCondition.onPlayerAdvancement(this.player.level(), this.player, advancementHolder);
    }
}
