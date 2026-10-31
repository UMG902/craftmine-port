package dev.craftmine.block;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

import dev.craftmine.duck.CraftmineDuckBridge;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineParticleTypes;
import dev.craftmine.server.CraftmineServerHooks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import dev.craftmine.block.entity.MineTravellingBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The mine portal: placed above the minecrafter when a crafted mine is
 * opened (or by the mine revisitor). Right-clicking travels into the stored
 * dimension; inside a mine it doubles as the exit (revisit blocks return to
 * the mine, ordinary ones take the player back to the hub once the mine is
 * won). Rendered INVISIBLE - the block entity's gateway is drawn by the
 * client renderer, with particles handled here.
 */
public class MineTravellingBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.cube(12.0);

    public MineTravellingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new MineTravellingBlockEntity(blockPos, blockState);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> blockEntityType) {
        return MineTravellingBlock.createTickerHelper(blockEntityType, CraftmineContent.MINE_TRAVELLING_BLOCK_ENTITY,
            level.isClientSide ? MineTravellingBlockEntity::gatewayAnimationTick : MineTravellingBlockEntity::portalTick);
    }

    @Override
    public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource random) {
        if (!(level.getBlockEntity(blockPos) instanceof MineTravellingBlockEntity blockEntity)) {
            return;
        }
        // Vanilla nether-portal particle pattern: bursts on two opposing faces, chosen at random.
        for (int i = 0; i < blockEntity.getParticleAmount() * 2; i++) {
            double x = blockPos.getX() + random.nextDouble();
            double y = blockPos.getY() + random.nextDouble();
            double z = blockPos.getZ() + random.nextDouble();
            double dx = (random.nextDouble() - 0.5) * 0.5;
            double dy = (random.nextDouble() - 0.5) * 0.5;
            double dz = (random.nextDouble() - 0.5) * 0.5;
            int face = random.nextInt(2) * 2 - 1;
            if (random.nextBoolean()) {
                z = blockPos.getZ() + 0.5 + 0.25 * face;
                dz = random.nextFloat() * 2.0f * face;
            } else {
                x = blockPos.getX() + 0.5 + 0.25 * face;
                dx = random.nextFloat() * 2.0f * face;
            }
            level.addParticle(CraftmineParticleTypes.MINE_TRAVEL, x, y, z, dx, dy, dz);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(blockPos) instanceof MineTravellingBlockEntity blockEntity) {
            CraftmineLevelDuck levelDuck = (CraftmineLevelDuck)serverLevel;
            if (blockEntity.isRevisitBlock() && !levelDuck.isMine()) {
                levelDuck.leaveForMine(blockPos, true, Optional.of(serverPlayer.getUUID()));
            } else if (levelDuck.isMine() && ((CraftminePlayerDuck)serverPlayer).isRevisiting()) {
                CraftmineDuckBridge.level(serverLevel).respawnPlayerIntoHub(serverPlayer, hubPlayer -> hubPlayer.getInventory().clearContent());
            } else {
                levelDuck.toggledMineTravellingBlock(blockPos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState, boolean includeContents) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canBeReplaced(BlockState blockState, Fluid fluid) {
        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.INVISIBLE;
    }

    /** Destroys whatever is at {@code blockPos} and places a configured travelling block there (plus the beacon sound). */
    public static void createBlock(Level level, BlockPos blockPos, ResourceKey<LevelStem> dimension, boolean revisit) {
        level.destroyBlock(blockPos, true, null);
        if (level.setBlock(blockPos, CraftmineContent.MINE_TRAVELLING_BLOCK.defaultBlockState(), 2)
            && level.getBlockEntity(blockPos) instanceof MineTravellingBlockEntity blockEntity) {
            blockEntity.setDimension(dimension);
            blockEntity.setRevisitBlock(revisit);
        }
        level.globalLevelEvent(1038, blockPos, 0);
    }
}
