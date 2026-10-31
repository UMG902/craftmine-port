/*
 * Ported from 25w14craftmine. Flat grid world generator ("mine" dimensions).
 * Remapped to the 26.3 pipeline: fillFromNoise -> buildTerrain, surface via
 * SurfaceSystem inside buildTerrain, no carvers (grid world has none).
 */
package dev.craftmine.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class GridChunkGenerator
extends ChunkGenerator {
    public static final MapCodec<GridChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(gridChunkGenerator -> gridChunkGenerator.biomeSource), NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(gridChunkGenerator -> gridChunkGenerator.settings), com.mojang.serialization.Codec.INT.fieldOf("spacing").forGetter(gridChunkGenerator -> gridChunkGenerator.spacing), com.mojang.serialization.Codec.INT.fieldOf("width").forGetter(gridChunkGenerator -> gridChunkGenerator.width), com.mojang.serialization.Codec.INT.fieldOf("y").forGetter(gridChunkGenerator -> gridChunkGenerator.y), com.mojang.serialization.Codec.BOOL.fieldOf("lines").forGetter(gridChunkGenerator -> gridChunkGenerator.lines)).apply(instance, instance.stable(GridChunkGenerator::new)));
    private final Holder<NoiseGeneratorSettings> settings;
    private final int spacing;
    private final int width;
    private final int y;
    private final boolean lines;

    public GridChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> holder, int n, int n2, int n3, boolean bl) {
        super(biomeSource);
        this.settings = holder;
        this.spacing = n;
        this.width = n2;
        this.y = n3;
        this.lines = bl;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public NoiseGeneratorSettings getNoiseGeneratorSettings() {
        return this.settings.value();
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunkAccess, Blender blender, RandomState randomState, StructureManager structureManager, BiomeManager biomeManager, WorldGenRegion worldGenRegion, Set<Holder<Biome>> set) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        Heightmap heightmap = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmap2 = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockState blockState = this.settings.value().defaultBlock();
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                if (!this.isSolid(i, j)) continue;
                chunkAccess.setBlockState(mutableBlockPos.set(i, this.y, j), blockState);
                heightmap.update(i, this.y, j, blockState);
                heightmap2.update(i, this.y, j, blockState);
            }
        }
        this.buildSurface(chunkAccess, new WorldGenerationContext(this, chunkAccess.getHeightAccessorForGeneration()), randomState, structureManager, biomeManager, set, blender);
        return CompletableFuture.completedFuture(chunkAccess);
    }

    private boolean isSolid(int n, int n2) {
        boolean bl;
        boolean bl2 = n % this.spacing < this.width;
        boolean bl3 = bl = n2 % this.spacing < this.width;
        return this.lines ? bl2 || bl : bl2 && bl;
    }

    private NoiseChunk createNoiseChunk(ChunkAccess chunkAccess, StructureManager structureManager, Blender blender, RandomState randomState) {
        NoiseSettings noiseSettings = this.settings.value().noiseSettings();
        Beardifier beardifier = Beardifier.forStructuresInChunk(structureManager, chunkAccess.getPos());
        return new NoiseChunk(randomState, beardifier, this.settings.value(), (n, n2, n3) -> new Aquifer.FluidStatus(-64, Blocks.AIR.defaultBlockState()), blender, new DensityVolume(16, noiseSettings.height(), 16, chunkAccess.getPos().getMinBlockX(), noiseSettings.minY(), chunkAccess.getPos().getMinBlockZ()));
    }

    private void buildSurface(ChunkAccess chunkAccess, WorldGenerationContext worldGenerationContext, RandomState randomState, StructureManager structureManager, BiomeManager biomeManager, Set<Holder<Biome>> set, Blender blender) {
        try (NoiseChunk noiseChunk = this.createNoiseChunk(chunkAccess, structureManager, blender, randomState);){
            randomState.surfaceSystem().buildSurface(randomState, biomeManager, worldGenerationContext, chunkAccess, noiseChunk, this.settings.value().materialRule().value(), set);
        }
    }

    @Override
    public int getSeaLevel() {
        return -63;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int n, int n2, Heightmap.Types types, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        return this.isSolid(n, n2) ? this.y : this.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int n, int n2, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        return this.isSolid(n, n2) ? this.hitColumn() : this.missColumn();
    }

    private NoiseColumn hitColumn() {
        NoiseColumn noiseColumn = this.missColumn();
        noiseColumn.setBlock(this.y, this.settings.value().defaultBlock());
        return noiseColumn;
    }

    private NoiseColumn missColumn() {
        BlockState[] blockStateArray = new BlockState[this.getGenDepth()];
        Arrays.fill(blockStateArray, Blocks.AIR.defaultBlockState());
        return new NoiseColumn(this.getMinY(), blockStateArray);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos blockPos, net.minecraft.world.level.levelgen.densityfunction.SamplerContext samplerContext) {
    }
}
