/*
 * Ported from 25w14craftmine. Generates the hub world out of hub structure templates.
 * Remapped to 26.3: buildTerrain, no carvers/surface hooks, template manager accessor.
 */
package dev.craftmine.worldgen;

import net.minecraft.world.level.levelgen.Heightmap;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.RegistryOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import dev.craftmine.registry.CraftmineContent;
import net.minecraft.world.level.levelgen.RandomState;

public class HubLevelSource
extends ChunkGenerator {
    public static final MapCodec<HubLevelSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(RegistryOps.retrieveElement(CraftmineContent.HUB_BIOME)).apply(instance, instance.stable(HubLevelSource::new)));
    public static final Identifier CENTER_BASE_STRUCTURE = Identifier.withDefaultNamespace("hub/center_base");
    public static final Identifier CENTER_TOP_STRUCTURE = Identifier.withDefaultNamespace("hub/center_hat");
    public static final Identifier CORRIDOR_BASE_STRUCTURE = Identifier.withDefaultNamespace("hub/corridor_base");
    public static final Identifier CORRIDOR_TOP_STRUCTURE = Identifier.withDefaultNamespace("hub/corridor_hat");
    private static final int BOTTOM_SECTION = 0;
    public static final StructureProcessor SKYIFIER = new RuleProcessor(List.of(new ProcessorRule(new BlockMatchTest(Blocks.BEDROCK), AlwaysTrueTest.INSTANCE, CraftmineContent.SKY.defaultBlockState())));

    public HubLevelSource(Holder.Reference<Biome> reference) {
        super(new FixedBiomeSource(reference));
    }

    protected MapCodec<HubLevelSource> codec() {
        return CODEC;
    }

    @Override
    public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel worldGenLevel, ChunkAccess chunkAccess, StructureManager structureManager) {
        ChunkPos chunkPos = chunkAccess.getPos();
        StructureTemplateManager structureTemplateManager = worldGenLevel.getLevel().getStructureTemplateManager();
        if (chunkPos.z() >= -1 && chunkPos.z() <= 1) {
            LevelHeightAccessor levelHeightAccessor = chunkAccess.getHeightAccessorForGeneration();
            BoundingBox boundingBox = new BoundingBox(chunkPos.getMinBlockX(), levelHeightAccessor.getMinY(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), levelHeightAccessor.getMaxY(), chunkPos.getMaxBlockZ());
            if (chunkPos.x() >= -1 && chunkPos.x() <= 1) {
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(-16, 0, -16);
                this.plaseChunk(worldGenLevel, structureTemplateManager, boundingBox, mutableBlockPos, CENTER_BASE_STRUCTURE, CENTER_TOP_STRUCTURE);
            } else if (chunkPos.x() < -1) {
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(chunkPos.x() * 16, 0, -16);
                this.plaseChunk(worldGenLevel, structureTemplateManager, boundingBox, mutableBlockPos, CORRIDOR_BASE_STRUCTURE, CORRIDOR_TOP_STRUCTURE);
            }
        }
    }

    private void plaseChunk(net.minecraft.world.level.WorldGenLevel worldGenLevel, StructureTemplateManager structureTemplateManager, BoundingBox boundingBox, BlockPos.MutableBlockPos mutableBlockPos, Identifier resourceLocation, Identifier resourceLocation2) {
        for (int i = 0; i < worldGenLevel.getMaxY() / 16; ++i) {
            Identifier resourceLocation3 = i == 0 ? resourceLocation : resourceLocation2;
            HubLevelSource.placeStructure(worldGenLevel, mutableBlockPos, boundingBox, structureTemplateManager, resourceLocation3);
            mutableBlockPos.move(net.minecraft.core.Direction.UP, 16);
        }
    }

    private static void placeStructure(net.minecraft.world.level.WorldGenLevel worldGenLevel, BlockPos blockPos, BoundingBox boundingBox, StructureTemplateManager structureTemplateManager, Identifier resourceLocation) {
        structureTemplateManager.get(resourceLocation).ifPresent(structureTemplate -> {
            WorldgenRandom worldgenRandom = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
            worldgenRandom.setDecorationSeed(worldGenLevel.getSeed(), blockPos.getX(), blockPos.getZ());
            structureTemplate.placeInWorld(worldGenLevel, blockPos, blockPos, new StructurePlaceSettings().setBoundingBox(boundingBox).addProcessor(SKYIFIER), worldgenRandom, 0);
        });
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
    }

    @Override
    public int getGenDepth() {
        return 0;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunkAccess, Blender blender, RandomState randomState, StructureManager structureManager, BiomeManager biomeManager, WorldGenRegion worldGenRegion, java.util.Set<Holder<Biome>> set) {
        return CompletableFuture.completedFuture(chunkAccess);
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getBaseHeight(int n, int n2, Heightmap.Types types, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        return 0;
    }

    @Override
    public NoiseColumn getBaseColumn(int n, int n2, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        return new NoiseColumn(0, new BlockState[0]);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos blockPos, net.minecraft.world.level.levelgen.densityfunction.SamplerContext samplerContext) {
    }
}
