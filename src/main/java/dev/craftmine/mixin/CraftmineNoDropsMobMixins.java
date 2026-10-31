/*
 * FT-8 gameplay layer - WorldEffects.NO_DROPS for the 26.3 mobs that keep
 * hardcoded dropCustomDeathLoot specials (the fork guarded these exact
 * methods: WitherBoss nether star, Creeper charged skull [now routed through
 * the shared 5-arg dropFromLootTable, guarded in LivingEntityMixin], Skeleton
 * /WitherSkeleton/Zombie [specials now live in loot tables, covered there]).
 * Piglin: 26.3 drops its entire inventory here (fork Piglin.dropCustomDeathLoot
 * was likewise guarded).
 */
package dev.craftmine.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class CraftmineNoDropsMobMixins {
    private CraftmineNoDropsMobMixins() {
    }

    @Mixin(net.minecraft.world.entity.boss.wither.WitherBoss.class)
    public abstract static class WitherBossMixin {
        @Inject(method = "dropCustomDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V", at = @At("HEAD"), cancellable = true)
        private void craftmine$noDrops(ServerLevel serverLevel, DamageSource damageSource, boolean bl, CallbackInfo ci) {
            if (dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, dev.craftmine.mines.WorldEffects.NO_DROPS)) {
                ci.cancel();
            }
        }
    }

    @Mixin(net.minecraft.world.entity.monster.piglin.Piglin.class)
    public abstract static class PiglinMixin {
        @Inject(method = "dropCustomDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V", at = @At("HEAD"), cancellable = true)
        private void craftmine$noDrops(ServerLevel serverLevel, DamageSource damageSource, boolean bl, CallbackInfo ci) {
            if (dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, dev.craftmine.mines.WorldEffects.NO_DROPS)) {
                ci.cancel();
            }
        }
    }
}
