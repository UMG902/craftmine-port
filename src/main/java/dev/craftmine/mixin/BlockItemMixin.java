/*
 * Mixin: craftmine additions to BlockItem (ports the 25w14craftmine
 * UnlockCondition.onBlockPlace dispatch).
 *
 * Fork call site: BlockItem.place, right before the PLACED_BLOCK criteria
 * trigger, guarded by player instanceof ServerPlayer && level instanceof
 * ServerLevel. 26.3's PLACED_BLOCK trigger is an ItemUsedOnLocationTrigger
 * (26.3 renamed/merged the trigger classes) - there is exactly one such call
 * inside place(), so no ordinal is needed.
 */
package dev.craftmine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import dev.craftmine.unlock.UnlockCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/triggers/ItemUsedOnLocationTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemInstance;)V", shift = At.Shift.BEFORE))
    private void craftmine$onBlockPlace(BlockPlaceContext blockPlaceContext, CallbackInfoReturnable cir) {
        Level level = blockPlaceContext.getLevel();
        if (blockPlaceContext.getPlayer() instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            BlockPos blockPos = blockPlaceContext.getClickedPos();
            UnlockCondition.onBlockPlace(level, serverPlayer, blockPos, level.getBlockState(blockPos));
        }
    }
}
