/*
 * FT-8 gameplay layer - YOU_ARE_THE_CAMPFIRE unlock (server game-mode part).
 * Fork ServerPlayerGameMode.handleBlockBreakAction START_DESTROY path
 * (bytecode + decompile verified):
 *   - punching FIRE while bare-handed and campfire -> return (walk through /
 *     punch fire without destroying it; mirrors the client MultiPlayerGameMode
 *     guard)
 *   - after blockState.attack(...), bare-handed campfire -> the punched block
 *     ignites (fork called FlintAndSteelItem.ignite with a centered hit
 *     result; 26.3 lost that helper so the ignite is inlined via
 *     BaseFireBlock.getState/canBePlacedAt like the 1.21.5 helper did).
 */
package dev.craftmine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
public abstract class CraftmineCampfireGameModeMixin {
    @Shadow
    public net.minecraft.server.level.ServerLevel level;

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleBlockBreakAction(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;attack(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V", shift = At.Shift.AFTER))
    private void craftmine$campfirePunchIgnite(BlockPos blockPos, ServerboundPlayerActionPacket.Action action, Direction direction, int i, int j, CallbackInfo ci) {
        if (dev.craftmine.duck.CraftmineDuckBridge.isActive(this.player, dev.craftmine.unlock.PlayerUnlocks.YOU_ARE_THE_CAMPFIRE)
            && this.player.getMainHandItem().isEmpty()) {
            craftmine$ignite(this.level, this.player, blockPos, direction);
        }
    }

    @Inject(method = "handleBlockBreakAction(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;onHitBlock(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Consumer;)V"),
        cancellable = true)
    private void craftmine$campfireFirePassThrough(BlockPos blockPos, ServerboundPlayerActionPacket.Action action, Direction direction, int i, int j, CallbackInfo ci) {
        BlockState blockState = this.level.getBlockState(blockPos);
        if (blockState.is(Blocks.FIRE)
            && dev.craftmine.duck.CraftmineDuckBridge.isActive(this.player, dev.craftmine.unlock.PlayerUnlocks.YOU_ARE_THE_CAMPFIRE)
            && this.player.getMainHandItem().isEmpty()) {
            ci.cancel();
        }
    }

    private static void craftmine$ignite(Level level, ServerPlayer serverPlayer, BlockPos blockPos, Direction direction) {
        // inline of 1.21.5 FlintAndSteelItem.ignite(UseOnContext) for a
        // centered hit result on the punched block (fork behavior)
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(blockPos), direction, blockPos, true);
        BlockPos firePos = hit.getBlockPos();
        BlockState fireState = BaseFireBlock.getState(level, firePos);
        if (!BaseFireBlock.canBePlacedAt(level, firePos, direction)) {
            firePos = firePos.relative(direction);
            fireState = BaseFireBlock.getState(level, firePos);
            if (!BaseFireBlock.canBePlacedAt(level, firePos, direction)) {
                return;
            }
        }
        level.setBlock(firePos, fireState, 11);
        level.playSound(null, firePos.getX() + 0.5, firePos.getY() + 0.5, firePos.getZ() + 0.5,
            net.minecraft.sounds.SoundEvents.FLINTANDSTEEL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.4F + 0.8F);
    }
}
