/*
 * FT-8 gameplay layer - GATHERER unlock (the fork patched three blocks).
 *
 *  BeehiveBlockMixin      - shearing a full hive drops the honeycomb TWICE,
 *                           harvesting with the last glass bottle gives a
 *                           second honey bottle straight into the inventory.
 *  CropPlayerDestroyMixin - breaking any CropBlock runs the crop's
 *                           dropResources a second time (26.3 CropBlock no
 *                           longer declares playerDestroy, so the inject
 *                           targets Block.playerDestroy and gates on
 *                           instanceof CropBlock - dispatch-identical to the
 *                           fork's override since no intermediate class
 *                           overrides playerDestroy).
 *  SweetBerryBushMixin    - picking sweet berries yields 2 extra berries.
 */
package dev.craftmine.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class CraftmineGathererMixins {
    private CraftmineGathererMixins() {
    }

    @Mixin(BeehiveBlock.class)
    public abstract static class BeehiveBlockMixin {

        @Inject(method = "useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BeehiveBlock;dropHoneycomb(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)V", shift = At.Shift.AFTER))
        private void craftmine$gathererShears(ItemStack itemStack, BlockState blockState, Level level, net.minecraft.core.BlockPos blockPos, Player player, InteractionHand interactionHand, net.minecraft.world.phys.BlockHitResult blockHitResult, CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
            if (dev.craftmine.duck.CraftmineDuckBridge.isActive(player, dev.craftmine.unlock.PlayerUnlocks.GATHERER)
                && level instanceof ServerLevel serverLevel) {
                BeehiveBlock.dropHoneycomb(serverLevel, itemStack, blockState, level.getBlockEntity(blockPos), player, blockPos);
            }
        }

        @Inject(method = "useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V", shift = At.Shift.AFTER))
        private void craftmine$gathererBottle(ItemStack itemStack, BlockState blockState, Level level, net.minecraft.core.BlockPos blockPos, Player player, InteractionHand interactionHand, net.minecraft.world.phys.BlockHitResult blockHitResult, CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
            // fires right after the hand was swapped to the filled honey bottle
            if (itemStack.isEmpty()
                && dev.craftmine.duck.CraftmineDuckBridge.isActive(player, dev.craftmine.unlock.PlayerUnlocks.GATHERER)) {
                player.addItem(new ItemStack(Items.HONEY_BOTTLE));
            }
        }
    }

    @Mixin(Block.class)
    public abstract static class CropPlayerDestroyMixin {
        @Inject(method = "playerDestroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
        private void craftmine$gathererDoubleCropDrops(ServerLevel serverLevel, ServerPlayer serverPlayer, net.minecraft.core.BlockPos blockPos, BlockState blockState, BlockEntity blockEntity, ItemStack itemStack, CallbackInfo ci) {
            if ((Object) this instanceof CropBlock
                && dev.craftmine.duck.CraftmineDuckBridge.isActive(serverPlayer, dev.craftmine.unlock.PlayerUnlocks.GATHERER)) {
                Block.dropResources(blockState, serverLevel, blockPos, blockEntity, serverPlayer, itemStack);
            }
        }
    }

    @Mixin(SweetBerryBushBlock.class)
    public abstract static class SweetBerryBushMixin {
        @Inject(method = "useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V", shift = At.Shift.AFTER))
        private void craftmine$gathererBerries(BlockState blockState, Level level, net.minecraft.core.BlockPos blockPos, Player player, net.minecraft.world.phys.BlockHitResult blockHitResult, CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
            if (dev.craftmine.duck.CraftmineDuckBridge.isActive(player, dev.craftmine.unlock.PlayerUnlocks.GATHERER)) {
                Block.popResource(level, blockPos, new ItemStack(Items.SWEET_BERRIES, 2));
            }
        }
    }
}
