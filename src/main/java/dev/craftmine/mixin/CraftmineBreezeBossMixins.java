/*
 * Breeze boss mixins (task 80 - the "breeze_boss" special mine, unoriginal
 * features toggle).
 *
 * 1. LivingEntityMixin: every vanilla MACE_SMASH damage (the mace's smash
 *    attack - the ONLY damage type that can never come from a non-mace) is
 *    routed here at hurtServer HEAD:
 *      - if the target carries the sky-boss tag, the damage is forwarded to
 *        BreezeBossBattle.onBossSmashed (the damage itself is never dealt -
 *        the boss is permanently invulnerable; it is converted to descent);
 *      - otherwise, a smash landing on any Mob dispatches the new
 *        UnlockCondition.onPlayerSmashedEntity - the "unlocked by smashing a
 *        mob in a mine" gate of the breeze boss mine (a mace is inherently
 *        required; the condition itself additionally checks the config and
 *        that the level is a mine).
 * 2. BreezeWindChargeMixin: wind charges shot by the sky boss (tagged at
 *    spawn) explode with 2x the knockback and 2x the damage instead of the
 *    vanilla breeze charge explosion (see BreezeBossBattle.explodeBossCharge).
 *    Untagged charges (any other breeze, e.g. trial chambers) run vanilla.
 */
package dev.craftmine.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class CraftmineBreezeBossMixins {
    private CraftmineBreezeBossMixins() {
    }

    @Mixin(net.minecraft.world.entity.LivingEntity.class)
    public abstract static class LivingEntityMixin {
        @Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"))
        private void craftmine$maceSmashDispatch(ServerLevel serverLevel, DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> cir) {
            if (!damageSource.is(net.minecraft.world.damagesource.DamageTypes.MACE_SMASH)) {
                return;
            }
            Entity self = (Entity)(Object)this;
            if (self.entityTags().contains(dev.craftmine.mines.BreezeBossBattle.BOSS_TAG)) {
                dev.craftmine.mines.BreezeBossBattle.onBossSmashed(serverLevel, self, f);
                return;
            }
            if (!((Object)this instanceof net.minecraft.world.entity.Mob)) {
                return;
            }
            if (damageSource.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                dev.craftmine.unlock.UnlockCondition.onPlayerSmashedEntity(serverLevel, serverPlayer, self, f);
            }
        }
    }

    @Mixin(net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge.class)
    public abstract static class BreezeWindChargeMixin {
        @Inject(method = "explode(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
        private void craftmine$bossChargeBlast(net.minecraft.world.phys.Vec3 vec3, CallbackInfo ci) {
            Entity self = (Entity)(Object)this;
            if (!self.entityTags().contains(dev.craftmine.mines.BreezeBossBattle.BOSS_CHARGE_TAG)) {
                return;
            }
            ci.cancel();
            dev.craftmine.mines.BreezeBossBattle.explodeBossCharge(
                (net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge)(Object)this, vec3);
        }
    }
}
