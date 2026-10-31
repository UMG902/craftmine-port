/*
 * Mixin: craftmine additions to Animal (ports the 25w14craftmine
 * UnlockCondition.onFedAnimal dispatches).
 *
 * Fork call site: the animal's feed method fired onFedAnimal in BOTH
 * successful feeding branches (love mode + baby age-up), guarded by
 * player instanceof ServerPlayer. 26.3 keeps those branches in
 * Animal.mobInteract (setInLove / ageUp) - hook right before each.
 * This is how PIGLINS (golden carrot on a pig) and similar food-feeding
 * conditions fired.
 */
package dev.craftmine.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.craftmine.unlock.UnlockCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Animal.class)
public abstract class AnimalMixin {
    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Animal;setInLove(Lnet/minecraft/world/entity/player/Player;)V", shift = At.Shift.BEFORE))
    private void craftmine$onFedAnimalLove(Player player, InteractionHand interactionHand, CallbackInfoReturnable cir) {
        if (player instanceof ServerPlayer serverPlayer) {
            UnlockCondition.onFedAnimal((ServerLevel)player.level(), serverPlayer, (Animal)(Object)this, player.getItemInHand(interactionHand));
        }
    }

    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Animal;ageUp(IZ)V", shift = At.Shift.BEFORE))
    private void craftmine$onFedAnimalAgeUp(Player player, InteractionHand interactionHand, CallbackInfoReturnable cir) {
        if (player instanceof ServerPlayer serverPlayer) {
            UnlockCondition.onFedAnimal((ServerLevel)player.level(), serverPlayer, (Animal)(Object)this, player.getItemInHand(interactionHand));
        }
    }
}
