package dev.craftmine.block.entity;

import dev.craftmine.registry.CraftmineContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * State of the mine travelling block (the mine portal): which dimension it
 * leads to, whether it is a revisit block (placed by the mine revisitor for
 * re-entering an old mine) and its age, which drives the spawn "warming up"
 * animation and the periodic beam. Age is persisted so a half-formed portal
 * resumes where it left off after a reload.
 */
public class MineTravellingBlockEntity extends BlockEntity {
    private static final int SPAWN_TIME = 200;

    private long age;
    private ResourceKey<LevelStem> dimension = LevelStem.OVERWORLD;
    private boolean revisit;

    public MineTravellingBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(CraftmineContent.MINE_TRAVELLING_BLOCK_ENTITY, blockPos, blockState);
    }

    public long age() {
        return this.age;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("age", this.age);
        output.putBoolean("revisit", this.revisit);
        output.store("dimension", ResourceKey.codec(Registries.LEVEL_STEM), this.dimension);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.age = input.getLongOr("age", 0L);
        this.revisit = input.getBooleanOr("revisit", false);
        this.dimension = input.read("dimension", ResourceKey.codec(Registries.LEVEL_STEM)).orElse(LevelStem.OVERWORLD);
    }

    public void setDimension(ResourceKey<LevelStem> dimension) {
        this.dimension = dimension;
    }

    public void setRevisitBlock(boolean revisit) {
        this.revisit = revisit;
    }

    /** Client-only ticker: age drives the gateway shader animation. */
    public static void gatewayAnimationTick(Level level, BlockPos blockPos, BlockState blockState, MineTravellingBlockEntity blockEntity) {
        blockEntity.age++;
    }

    /** Server ticker: marks the BE changed whenever the spawning state flips (clients must re-sync). */
    public static void portalTick(Level level, BlockPos blockPos, BlockState blockState, MineTravellingBlockEntity blockEntity) {
        boolean spawning = blockEntity.isSpawning();
        blockEntity.age++;
        if (spawning != blockEntity.isSpawning()) {
            MineTravellingBlockEntity.setChanged(level, blockPos, blockState);
        }
    }

    public boolean isSpawning() {
        return this.age < SPAWN_TIME;
    }

    public boolean shouldPeriodicallyBeam() {
        return (int)this.age / SPAWN_TIME % 6 == 0;
    }

    public float periodicallyBeam(float partialTicks) {
        float beamPhase = ((float)this.age + partialTicks) % SPAWN_TIME;
        return Mth.clamp(beamPhase / SPAWN_TIME, 0.0f, 1.0f);
    }

    public float getSpawnPercent(float partialTicks) {
        return Mth.clamp(((float)this.age + partialTicks) / SPAWN_TIME, 0.0f, 1.0f);
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return this.saveCustomOnly(provider);
    }

    /** Vanilla-style culling: only render the faces not hidden by neighbours. */
    public boolean shouldRenderFace(Direction direction) {
        return Block.shouldRenderFace(this.getBlockState(), this.level.getBlockState(this.getBlockPos().relative(direction)), direction);
    }

    public int getParticleAmount() {
        int exposedFaces = 0;
        for (Direction direction : Direction.values()) {
            if (this.shouldRenderFace(direction)) {
                exposedFaces++;
            }
        }
        return exposedFaces;
    }

    public ResourceKey<Level> getTargetDimension() {
        return Registries.levelStemToLevel(this.dimension);
    }

    public boolean isRevisitBlock() {
        return this.revisit;
    }
}
