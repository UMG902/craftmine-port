/*
 * FT-8 gameplay layer - WorldEffects.ICY ground friction for the non-living
 * movers the fork patched (fork passed level().getIsIcy() into fork
 * Block.getFriction(boolean) at ExperienceOrb.aiStep, ItemEntity.tick and
 * AbstractBoat's water-friction scan; 26.3 keeps those exact paths but reads
 * plain Block.getFriction()). LivingEntity.travelInAir is covered by
 * LivingEntityMixin. Fork constant: 0.989 while icy.
 */
package dev.craftmine.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

public final class CraftmineIcyFrictionMixins {
    private CraftmineIcyFrictionMixins() {
    }

    @Mixin(net.minecraft.world.entity.ExperienceOrb.class)
    public abstract static class ExperienceOrbMixin {
        @ModifyExpressionValue(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
        private float craftmine$icyFriction(float original) {
            if (dev.craftmine.server.CraftmineGameplayEffects.isIcy((net.minecraft.world.entity.Entity) (Object) this)) {
                return 0.989F;
            }
            return original;
        }
    }

    @Mixin(net.minecraft.world.entity.item.ItemEntity.class)
    public abstract static class ItemEntityMixin {
        @ModifyExpressionValue(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
        private float craftmine$icyFriction(float original) {
            if (dev.craftmine.server.CraftmineGameplayEffects.isIcy((net.minecraft.world.entity.Entity) (Object) this)) {
                return 0.989F;
            }
            return original;
        }
    }

    @Mixin(net.minecraft.world.entity.vehicle.boat.AbstractBoat.class)
    public abstract static class AbstractBoatMixin {
        @ModifyExpressionValue(method = "getGroundFriction()F", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
        private float craftmine$icyFriction(float original) {
            if (dev.craftmine.server.CraftmineGameplayEffects.isIcy((net.minecraft.world.entity.Entity) (Object) this)) {
                return 0.989F;
            }
            return original;
        }
    }
}
