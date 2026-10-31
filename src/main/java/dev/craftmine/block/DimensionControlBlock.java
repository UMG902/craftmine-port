package dev.craftmine.block;

import com.mojang.serialization.MapCodec;

import dev.craftmine.server.CraftmineServerHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import dev.craftmine.inventory.DimensionControlMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The boiler-room sky control: a single-slot menu that swaps the level's
 * visual sky via the placed sky box item (see {@link DimensionControlMenu}
 * for the full sky/dimension-type mechanics).
 */
public class DimensionControlBlock extends Block {
    private static final Component CONTAINER_TITLE = Component.translatable("container.dimension_control");

    public DimensionControlBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Fork packet path: vanilla openMenu would send ClientboundOpenScreenPacket,
            // which the client drops for our unregistered-in-MenuScreens menu types.
            CraftmineServerHooks.openCraftmineMenu(serverPlayer, blockState.getMenuProvider(level, blockPos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState blockState, Level level, BlockPos blockPos) {
        return new SimpleMenuProvider((id, inventory, menuPlayer) -> new DimensionControlMenu(id, inventory, ContainerLevelAccess.create(level, blockPos)), CONTAINER_TITLE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState blockState) {
        // Drawn exclusively by the client-side SkyWindowRenderer (the snapshot's
        // sky_block pass): INVISIBLE keeps the vanilla/Sodium chunk meshers out.
        return RenderShape.INVISIBLE;
    }
}
