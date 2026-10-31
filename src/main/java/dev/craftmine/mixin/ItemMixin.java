/*
 * Mixin: craftmine behavior for the FEATHER item (ports the 25w14craftmine
 * FeatherItem, which the fork registered as the vanilla "feather" item via its
 * own Items.java; 26.3's runtime Items keeps a plain Item there and the port
 * has no in-tree Items.java, so the behavior rides a use() intercept instead
 * of a registry swap).
 *
 * Fork behavior (FeatherItem.use, 1:1): right-clicking a feather always
 * "succeeds"; when the user has the WING_GUARDIAN unlock it also applies
 * Levitation 5s + Slow Falling 10s. The unlock itself (wing_guardian_lift_off_
 * to_the_sky) is buyable in the port's unlock tree - before this mixin buying
 * it did nothing (verified gap from the task-64 AI-claims audit; the port's
 * FeatherItem class existed but was never registered for the feather id).
 *
 * Identity check instead of a registry swap: Items.FEATHER is the exact
 * instance vanilla 26.3 registered, so the hook is precise and cannot leak
 * into other items. Item.use is only the BASE implementation - subclasses
 * that override use() (exit eye, mine item, ...) never reach it.
 */
package dev.craftmine.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemMixin {

    @Inject(method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void craftmine$featherWingGuardian(Level level, Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        if ((Object) this != Items.FEATHER) {
            return;
        }
        if (level instanceof ServerLevel serverLevel
            && player instanceof ServerPlayer serverPlayer
            && ((dev.craftmine.duck.CraftminePlayerDuck) (Object) serverPlayer).isUnlocked(dev.craftmine.unlock.PlayerUnlocks.WING_GUARDIAN)) {
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100));
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200));
        }
        cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
    }
}
