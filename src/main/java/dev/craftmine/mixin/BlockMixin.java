/*
 * Mixin: craftmine additions to Block (ports the 25w14craftmine
 * UnlockCondition.onBlockDrops dispatches that 26.3 lost).
 *
 * The fork called UnlockCondition.onBlockDrops from EVERY Block.dropResources
 * overload (before drops are computed), which is how ingredient unlock
 * conditions like FORESTS/TAIGAS (1% on leaves) and the base_stone variants
 * (10% on blackstone/diorite/andesite/granite/tuff/deepslate) ever fired.
 * 26.3 has no such call -> those ingredients were unobtainable.
 */
package dev.craftmine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import dev.craftmine.unlock.UnlockCondition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"))
    private static void craftmine$onBlockDrops0(BlockState blockState, Level level, BlockPos blockPos, CallbackInfo ci) {
        if (level instanceof ServerLevel) {
            UnlockCondition.onBlockDrops((ServerLevel)level, blockPos, blockState);
        }
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V", at = @At("HEAD"))
    private static void craftmine$onBlockDrops1(BlockState blockState, LevelAccessor levelAccessor, BlockPos blockPos, BlockEntity blockEntity, CallbackInfo ci) {
        if (levelAccessor instanceof ServerLevel) {
            UnlockCondition.onBlockDrops((ServerLevel)levelAccessor, blockPos, blockState);
        }
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private static void craftmine$onBlockDrops2(BlockState blockState, Level level, BlockPos blockPos, BlockEntity blockEntity, Entity entity, ItemStack itemStack, CallbackInfo ci) {
        if (level instanceof ServerLevel) {
            UnlockCondition.onBlockDrops((ServerLevel)level, blockPos, blockState);
        }
    }

    /*
     * FT-8 gameplay layer - WorldEffects.BOUNCY.
     *
     * The fork gated the vanilla Block.stepOn / Block.fallOn bodies behind
     * Level.getIsBouncy() (fork Level.isIcy/isBouncy accessors):
     *
     *   fallOn: while bouncy, landings never deal fall damage
     *     - not suppressing bounce -> causeFallDamage(distance, 0.0f, fall)
     *     - suppressing bounce (sneak/fly) -> no damage call at all
     *   stepOn: while bouncy, every block pushes like a slime block
     *     - |dy| < 0.1 && !isSteppingCarefully -> multiply x/z by (0.4 + |dy|*0.2)
     *
     * 26.3 moved the slime-block behavior into SlimeBlock overrides of these
     * exact hooks, so injecting here reproduces the fork with the same
     * dispatch order (HEAD, cancellable) for every block in the level.
     */
    @Inject(method = "fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;D)V", at = @At("HEAD"), cancellable = true)
    private void craftmine$bouncyFallOn(Level level, BlockState blockState, BlockPos blockPos, Entity entity, double d, CallbackInfo ci) {
        if (dev.craftmine.server.CraftmineGameplayEffects.isBouncy(level)) {
            if (!entity.isSuppressingBounce()) {
                entity.causeFallDamage(d, 0.0F, level.damageSources().fall());
            }
            ci.cancel();
        }
    }

    @Inject(method = "stepOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void craftmine$bouncyStepOn(Level level, BlockPos blockPos, BlockState blockState, Entity entity, CallbackInfo ci) {
        if (dev.craftmine.server.CraftmineGameplayEffects.isBouncy(level)) {
            double d = Math.abs(entity.getDeltaMovement().y);
            if (d < 0.1 && !entity.isSteppingCarefully()) {
                double d2 = 0.4 + d * 0.2;
                entity.setDeltaMovement(entity.getDeltaMovement().multiply(d2, 1.0, d2));
            }
            ci.cancel();
        }
    }
}
