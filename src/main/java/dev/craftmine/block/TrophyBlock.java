package dev.craftmine.block;

import com.mojang.serialization.MapCodec;

import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * The statue earned for beating a boss mine. Its type (gold / mega spud /
 * no medal) travels via the TROPHY_TYPE data component on the item and is
 * mirrored into the block state on placement, so the renderer picks the
 * right model without extra block-entity machinery.
 */
public class TrophyBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<TrophyType> TYPE = CraftmineContent.TROPHY_TYPE_PROPERTY;

    public TrophyBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TYPE, TrophyType.GOLD));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CraftmineContent.TROPHY_TYPE_PROPERTY, FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        TrophyType trophyType = context.getItemInHand().getOrDefault(CraftmineDataComponents.TROPHY_TYPE, TrophyType.GOLD);
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(TYPE, trophyType);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState, boolean includeContents) {
        ItemStack itemStack = super.getCloneItemStack(levelReader, blockPos, blockState, includeContents);
        itemStack.set(CraftmineDataComponents.TROPHY_TYPE, blockState.getValue(TYPE));
        return itemStack;
    }

    public static ItemStack createTrophy(TrophyType trophyType) {
        ItemStack itemStack = new ItemStack(CraftmineContent.TROPHY);
        itemStack.set(CraftmineDataComponents.TROPHY_TYPE, trophyType);
        return itemStack;
    }
}
