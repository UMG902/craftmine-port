package dev.craftmine.block;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

import dev.craftmine.server.CraftmineServerHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.item.component.WorldModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import dev.craftmine.block.entity.MineCrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import dev.craftmine.mines.WorldEffect;
import net.minecraft.world.phys.BlockHitResult;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineDataComponents;

import dev.craftmine.Craftmine;

/**
 * The mine crafter: the block this whole mod is named after. Right-click
 * opens the mine crafting menu (via the fork's open-window packet path);
 * right-clicking with an unlock effect item unlocks that effect directly.
 * Ticks server-side to watch the placed travelling block and dispense the
 * finished mine (see {@link MineCrafterBlockEntity#serverTick}).
 */
public class MineCrafterBlock extends BaseEntityBlock {

    public MineCrafterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
        if (level instanceof ServerLevel serverLevel) {
            MineCrafterBlockEntity blockEntity =
                level.getBlockEntity(blockPos) instanceof MineCrafterBlockEntity existing ? existing : null;
            if (blockEntity == null) {
                // 26.3 structure placement does not reliably promote the hub
                // template's per-block BE data for the mine crafter, so the hub
                // block can arrive BE-less (probe: data get block -> "not a
                // block entity"). The original always has a live BE here.
                // Self-heal so right-click opens the GUI regardless of how the
                // block arrived.
                blockEntity = new MineCrafterBlockEntity(blockPos, blockState);
                serverLevel.setBlockEntity(blockEntity);
                Craftmine.LOGGER.info("Craftmine: self-healed missing mine crafter block entity at {}", blockPos.toShortString());
            }
            // Fork packet path: vanilla openMenu sends ClientboundOpenScreenPacket, which
            // the client silently drops for the unregistered map_making menu - the GUI
            // never opened. CraftmineServerHooks sends the fork's ClientboundOpenWindowPacket.
            Craftmine.LOGGER.info("Craftmine: mine crafter used at {} - opening map making menu", blockPos.toShortString());
            CraftmineServerHooks.openCraftmineMenu((ServerPlayer)player, blockEntity);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (itemStack.has(CraftmineDataComponents.WORLD_EFFECT_UNLOCK) && itemStack.has(CraftmineDataComponents.WORLD_MODIFIERS)) {
            if (level instanceof ServerLevel serverLevel) {
                WorldModifiers worldModifiers = itemStack.get(CraftmineDataComponents.WORLD_MODIFIERS);
                for (WorldEffect worldEffect : worldModifiers.effects()) {
                    ((CraftmineLevelDuck)serverLevel).unlockEffect(worldEffect);
                }
            }
            itemStack.setCount(0);
            return InteractionResult.CONSUME;
        }
        return super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new MineCrafterBlockEntity(blockPos, blockState);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> blockEntityType) {
        return MineCrafterBlock.createTickerHelper(blockEntityType, CraftmineContent.MINE_CRAFTER_BLOCK_ENTITY, MineCrafterBlockEntity::serverTick);
    }
}
