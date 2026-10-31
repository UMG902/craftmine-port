/*
 * FT-8 gameplay layer - mob-side behaviors the fork patched into vanilla
 * classes, grouped here (each nested class is a separate mixin):
 *
 *  MobMixin            - HUNTER unlock: the fork's Animal overrode
 *                        dropFromLootTable to run the loot table a SECOND time
 *                        (recentlyHit=true) when killed by a HUNTER player.
 *                        26.3 Animal no longer declares that method, so we
 *                        inject into Mob's (the declaring class) and gate on
 *                        instanceof Animal - bytecode-verified: the fork's
 *                        Mob-level hunter branch is an empty no-op, only the
 *                        Animal override does anything. Needs a re-entrancy
 *                        guard (see MobMixin) because the second run goes
 *                        through the virtual method again.
 *  NeutralMobAngerMixin- WorldEffects.UNIVERSAL_ANGER: fork patched
 *                        isAngryAtAllPlayers to return true before the
 *                        gamerule check (all neutral mobs aggro everyone).
 *                        WorldEffects.BEES: the fork's Bee overrode
 *                        isAngryAt/isAngryAtAllPlayers to always report angry
 *                        while the effect is active (26.3 Bee no longer
 *                        declares either - gated on instanceof Bee here).
 *  HurtByTargetGoalMixin / ResetUniversalAngerTargetGoalMixin
 *                      - fork added "|| level.isActive(UNIVERSAL_ANGER)" to
 *                        the vanilla gamerule gates in both goals.
 *  FrogMixin           - WEDNESDAY_FROGS: canEat accepts ServerPlayers while
 *                        the effect is active (frogs hunt players).
 *  ShootTongueMixin    - WEDNESDAY_FROGS: the tongue eat deals the fork's
 *                        9001.0f "devour" damage instead of the normal bite.
 */
package dev.craftmine.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.player.Player;
import dev.craftmine.mines.WorldEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class CraftmineMobGameplayMixins {
    private CraftmineMobGameplayMixins() {
    }

    @Mixin(Mob.class)
    public abstract static class MobMixin {
        /*
         * Re-entrancy guard for the double drop below: the invoker call goes
         * through the VIRTUAL dropFromLootTable, which re-enters this very
         * handler at its TAIL. Without the guard the handler recurses
         * handler -> invoker -> dropFromLootTable -> handler ... until
         * StackOverflowError kills the server thread (crash
         * 2026-10-27_23.05.42-server.txt, "Exception in server tick loop",
         * dropped while a HUNTER player's kill generated its loot). The fork
         * never recursed because its Animal override used a super-call.
         */
        @Unique
        private boolean craftmine$doubleDropping;

        @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V", at = @At("TAIL"))
        private void craftmine$hunterDoubleDrop(ServerLevel serverLevel, DamageSource damageSource, boolean bl, CallbackInfo ci) {
            if (this.craftmine$doubleDropping) {
                return;
            }
            if (bl && (Object) this instanceof Animal
                && damageSource.getEntity() instanceof Player player
                && dev.craftmine.duck.CraftmineDuckBridge.isActive(player, dev.craftmine.unlock.PlayerUnlocks.HUNTER)) {
                this.craftmine$doubleDropping = true;
                try {
                    ((CraftmineLivingEntityInvoker) (Object) this).craftmine$invokeDropFromLootTable(serverLevel, damageSource, true);
                } finally {
                    this.craftmine$doubleDropping = false;
                }
            }
        }
    }

    @Mixin(NeutralMob.class)
    public interface NeutralMobAngerMixin {
        @Inject(method = "isAngryAtAllPlayers(Lnet/minecraft/server/level/ServerLevel;)Z", at = @At("HEAD"), cancellable = true)
        default void craftmine$angryAtAllPlayers(ServerLevel serverLevel, CallbackInfoReturnable<Boolean> cir) {
            if (dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, WorldEffects.UNIVERSAL_ANGER)) {
                cir.setReturnValue(true);
                return;
            }
            if ((Object) this instanceof Bee
                && dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, WorldEffects.BEES)) {
                cir.setReturnValue(true);
            }
        }

        @Inject(method = "isAngryAt(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerLevel;)Z", at = @At("HEAD"), cancellable = true)
        default void craftmine$beesAngryAtEverything(LivingEntity livingEntity, ServerLevel serverLevel, CallbackInfoReturnable<Boolean> cir) {
            if ((Object) this instanceof Bee
                && dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, WorldEffects.BEES)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Mixin(HurtByTargetGoal.class)
    public abstract static class HurtByTargetGoalMixin {
        // mob is declared on TargetGoal (superclass) - @Shadow cannot resolve
        // inherited fields (task-31 lesson), so read it through an accessor mixin.
        @ModifyExpressionValue(
            method = "canUse()Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
        private Object craftmine$universalAnger(Object original) {
            if (Boolean.TRUE.equals(original)) {
                return true;
            }
            Mob mob = ((TargetGoalMobAccessor) (Object) this).craftmine$mob();
            return mob.level() instanceof ServerLevel serverLevel
                && dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, WorldEffects.UNIVERSAL_ANGER);
        }
    }

    @Mixin(net.minecraft.world.entity.ai.goal.target.TargetGoal.class)
    public interface TargetGoalMobAccessor {
        @org.spongepowered.asm.mixin.gen.Accessor("mob")
        net.minecraft.world.entity.Mob craftmine$mob();
    }

    @Mixin(ResetUniversalAngerTargetGoal.class)
    public abstract static class ResetUniversalAngerTargetGoalMixin {
        @Shadow
        protected Mob mob;

        @ModifyExpressionValue(
            method = "canUse()Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
        private Object craftmine$universalAnger(Object original) {
            if (Boolean.TRUE.equals(original)) {
                return true;
            }
            return this.mob.level() instanceof ServerLevel serverLevel
                && dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, WorldEffects.UNIVERSAL_ANGER);
        }
    }

    @Mixin(Frog.class)
    public abstract static class FrogMixin {
        @Inject(method = "canEat(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
        private static void craftmine$wednesdayPlayers(LivingEntity livingEntity, CallbackInfoReturnable<Boolean> cir) {
            if (livingEntity instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && dev.craftmine.server.CraftmineGameplayEffects.isActive((ServerLevel)serverPlayer.level(), WorldEffects.WEDNESDAY_FROGS)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Mixin(net.minecraft.world.entity.animal.frog.ShootTongue.class)
    public abstract static class ShootTongueMixin {
        @Redirect(
            method = "eatEntity(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/frog/Frog;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/frog/Frog;doHurtTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z"))
        private boolean craftmine$devour(Frog frog, ServerLevel serverLevel, Entity entity) {
            if (dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, WorldEffects.WEDNESDAY_FROGS)) {
                return entity.hurtServer(serverLevel, dev.craftmine.server.CraftmineDevourDamage.devour(serverLevel, frog), 9001.0F);
            }
            return frog.doHurtTarget(serverLevel, entity);
        }
    }
}
