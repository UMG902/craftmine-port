package dev.craftmine.item;

import dev.craftmine.registry.CraftmineContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

public class ShimmeringKeyItem
extends Item {
    public ShimmeringKeyItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack itemStack) {
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext useOnContext) {
        BlockPos blockPos;
        Player player = useOnContext.getPlayer();
        Level level = useOnContext.getLevel();
        BlockState blockState = level.getBlockState(blockPos = useOnContext.getClickedPos());
        if (blockState.is(CraftmineContent.SHIMMERING_DOOR)) {
            if (blockState.getValue(DoorBlock.OPEN).booleanValue()) {
                return InteractionResult.FAIL;
            }
            if (player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)player;
                ((dev.craftmine.duck.CraftminePlayerDuck)(Object)serverPlayer).openDoor(useOnContext.getItemInHand(), blockPos, blockState.getValue(DoorBlock.FACING), blockState.getValue(DoorBlock.HALF));
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        return super.useOn(useOnContext);
    }
}

