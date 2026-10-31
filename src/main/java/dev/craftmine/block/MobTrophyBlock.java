package dev.craftmine.block;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

import dev.craftmine.block.entity.MobTrophyBlockEntity;
import dev.craftmine.item.MobTrophyInfo;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The mob trophy block: a wall-mounted bust of a killed mob, with a random
 * grade ({@link Grade}) that mimics ore materials. Right-clicking plays one
 * of the trophy mob's own sound events; the mob identity lives on the block
 * entity's MOB_TROPHY_TYPE data component.
 */
public class MobTrophyBlock extends BaseEntityBlock {
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.column(12.0, 0.0, 16.0));
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Grade> GRADE = EnumProperty.create("grade", Grade.class);

    public MobTrophyBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(GRADE, Grade.GRASS));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
        builder.add(GRADE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new MobTrophyBlockEntity(blockPos, blockState);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState, boolean includeContents) {
        ItemStack itemStack = super.getCloneItemStack(levelReader, blockPos, blockState, includeContents);
        itemStack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(GRADE, blockState.getValue(GRADE)));
        return itemStack;
    }

    @Override
    protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
        return SHAPES.get(blockState.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState blockState, Rotation rotation) {
        return blockState.setValue(FACING, rotation.rotate(blockState.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState blockState, Mirror mirror) {
        return blockState.setValue(FACING, mirror.mirror(blockState.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getBlockEntity(blockPos, CraftmineContent.MOB_TROPHY_BLOCK_ENTITY).ifPresent(mobTrophyBlockEntity -> {
                MobTrophyInfo mobTrophyInfo = mobTrophyBlockEntity.getEntityType();
                if (mobTrophyInfo == null) {
                    return;
                }
                mobTrophyInfo.type().unwrapKey().ifPresent(typeKey -> {
                    // Every sound event whose id starts with the mob's translation
                    // key prefix belongs to that mob - play one at random.
                    String prefix = "entity." + typeKey.identifier().getPath() + ".";
                    List<Holder.Reference<SoundEvent>> mobSounds = serverLevel.registryAccess().lookupOrThrow(Registries.SOUND_EVENT).listElements()
                        .filter(sound -> sound.key().identifier().getPath().startsWith(prefix))
                        .toList();
                    if (!mobSounds.isEmpty()) {
                        level.playSound(null, blockPos, mobSounds.get(level.random.nextInt(mobSounds.size())).value(), SoundSource.BLOCKS);
                    }
                });
            });
        }
        return InteractionResult.PASS;
    }

    /** Rolls a random trophy item for the mob: weighted grade + 1% shiny variant. */
    public static ItemStack generateForMob(Holder<EntityType<?>> entityType, RandomSource random) {
        Grade grade = Grade.GRADES.getRandom(random).orElseThrow();
        boolean shiny = random.nextFloat() < 0.01;
        ItemStack itemStack = new ItemStack(CraftmineContent.MOB_TROPHY);
        itemStack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(GRADE, grade));
        itemStack.set(CraftmineDataComponents.MOB_TROPHY_TYPE, new MobTrophyInfo(entityType, shiny));
        return itemStack;
    }

    /** Trophy material grade, from common (grass) to the 1-in-181 netherite pull. */
    public static enum Grade implements StringRepresentable {
        GRASS("grass", 5295618),
        STONE("stone", 4673362),
        GOLD("gold", 14594349),
        DIAMOND("diamond", 7269586),
        NETHERITE("netherite", 6445145);

        public static final WeightedList<Grade> GRADES = WeightedList.of(
            new Weighted<>(GRASS, 100), new Weighted<>(STONE, 50), new Weighted<>(GOLD, 25), new Weighted<>(DIAMOND, 5), new Weighted<>(NETHERITE, 1));

        private final String name;
        private final int color;

        Grade(String name, int color) {
            this.name = name;
            this.color = color;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public int color() {
            return this.color;
        }

        public String translationId() {
            return "item.minecraft.mob_trophy.grade." + this.name;
        }
    }
}
