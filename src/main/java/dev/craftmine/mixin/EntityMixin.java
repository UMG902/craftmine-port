/*
 * Mixin: craftmine additions to Entity - FT-8 gameplay layer,
 * WorldEffects.BOUNCY (world effect "bouncy" - every surface behaves like a
 * slime block while the effect is active).
 *
 * The fork implemented the bounce-up in Block.updateEntityMovementAfterFallOn
 * (called from fork Entity.checkFallDamage): while bouncy,
 *   - suppressing bounce (sneak/gliding) -> keep the vanilla y=0 landing
 *   - otherwise bounceUp(): y = -y * (LivingEntity ? 1.0 : 0.8) when falling
 * 26.3 restructured this into Entity.restituteMovementAfterCollisions, which
 * resolves the landing restitution from Entity.getBlockBounciness(Block) =
 * max(entity-side, block.getBounceRestitution() [* 0.8 for non-living]).
 *
 * We therefore raise the resolved bounciness to the fork's constants while
 * the owning level has BOUNCY active: LivingEntity -> 1.0, other -> 0.8
 * (exactly the two multipliers fork bounceUp used). The vanilla reflection
 * math then produces the fork's bounce, and suppressing-bounce still lands
 * flat (26.3 zeroes the block contribution in that case).
 */
package dev.craftmine.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @ModifyReturnValue(method = "getBlockBounciness(Lnet/minecraft/world/level/block/Block;)D", at = @At("RETURN"))
    private double craftmine$bouncyWorld(double original, net.minecraft.world.level.block.Block block) {
        Entity self = (Entity) (Object) this;
        if (dev.craftmine.server.CraftmineGameplayEffects.isBouncy(self)) {
            double forkBounciness = self instanceof net.minecraft.world.entity.LivingEntity ? 1.0 : 0.8;
            return Math.max(original, forkBounciness);
        }
        return original;
    }
}
