package dev.craftmine.block;

import com.mojang.serialization.MapCodec;

import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.registry.CraftmineTriggers;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Receives a completed-mine item (right-click with it): places a revisit
 * travelling block above itself that teleports the player back into that
 * mine. The mine item is consumed and the activation counts as a criterion.
 */
public class MineRevisitorBlock extends Block {

    public MineRevisitorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (itemStack.has(CraftmineDataComponents.MINE_COMPLETED) && itemStack.has(CraftmineDataComponents.DIMENSION_ID)) {
            ResourceKey<LevelStem> dimension = itemStack.get(CraftmineDataComponents.DIMENSION_ID);
            if (dimension != null) {
                MineTravellingBlock.createBlock(level, blockPos.above(), dimension, true);
                itemStack.consume(1, player);
                if (player instanceof ServerPlayer serverPlayer) {
                    CraftmineTriggers.MINE_REVISITOR_ACTIVATED.trigger(serverPlayer);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
    }
}
