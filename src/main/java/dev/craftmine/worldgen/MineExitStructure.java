/*
 * Task 42: port of the 25w14craftmine exit-jigsaw structure (fork exv).
 *
 * The fork edited its vanilla Structure/JigsawStructure to (a) make "biomes"
 * optional and (b) add "required_exit_type" (fork dok$b), then filtered the
 * biome set at placement time (fork evu.a(jl): biomes.orElse(all biomes from
 * the level's biome source) filtered to biomes whose exit type matches).
 *
 * 26.3's JigsawStructure is final and its StructureSettings codec requires
 * "biomes", so this is a 26.3-adapted re-port:
 * - own StructureType "craftmine:mine_exit" (registered in CraftmineContent);
 * - settings codec replaces the vanilla one: biomes is dropped entirely (our
 *   three exit structure JSONs never had it) and "required_exit_type" is added
 *   (default NONE = vanilla-identical behavior, though vanilla structures do
 *   not use this type);
 * - biomes() is overridden with a dynamic set: contains(holder) matches when
 *   the biome's craftmine exit type (CraftmineBiomeExitTypes, keyed by the
 *   mine's modified biome ids) equals this structure's required exit type.
 *   StructureCheck's generation-point predicate is the only contains() caller;
 * - the two stream() consumers in ChunkGeneratorStructureState
 *   (hasBiomesForStructureSet / generatePositions) are re-routed by
 *   ChunkGeneratorStructureStateMixin, which filters the level's actual
 *   possible-biomes set by exit type (a dynamic set has no universe to stream);
 * - the fork-only "project_start_to_surface" flag is honored in
 *   findGenerationPoint: fork ewy.a scans the noise column for the first
 *   solid-with-air-above block (down, then up, then give up) and 26.3's
 *   addPieces moves the start piece so box.minY + groundLevelDelta lands on
 *   position.y when no heightmap projection is set - so pre-projecting the
 *   start position y reproduces the fork exactly. Documented deviation: the
 *   fork scans the column at the START PIECE's bounding-box center, which is
 *   only known inside addPieces after the random rotation is drawn; consuming
 *   the rotation early would shift the level's RNG stream, so we scan the
 *   chunk-origin column (position x/z) instead - at most a few blocks of
 *   horizontal difference on ragged cave floors.
 */
package dev.craftmine.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.craftmine.registry.CraftmineContent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import dev.craftmine.level.biome.CraftmineBiomeExitTypes;
import dev.craftmine.level.biome.CraftmineExitType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

public class MineExitStructure
extends JigsawStructure {
    // NOTE: the Instance<MineExitStructure> lambda parameter type is explicit on
    // purpose - with a 15-field group whose forGetter lambdas read members of
    // the owner type, javac's inference leaves O unresolved (every lambda
    // parameter degrades to Object and the ctor method reference goes stale).
    // Pinning O in the lambda signature resolves the whole chain.
    public static final MapCodec<MineExitStructure> CODEC = RecordCodecBuilder.mapCodec((RecordCodecBuilder.Instance<MineExitStructure> instance) -> instance.group(
        Codec.simpleMap(MobCategory.CODEC, StructureSpawnOverride.CODEC, StringRepresentable.keys(MobCategory.values())).fieldOf("spawn_overrides").forGetter(structure -> structure.spawnOverrides()),
        CraftmineExitType.CODEC.optionalFieldOf("required_exit_type", CraftmineExitType.NONE).forGetter(structure -> structure.requiredExitType),
        GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(structure -> structure.step()),
        TerrainAdjustment.CODEC.optionalFieldOf("terrain_adaptation", TerrainAdjustment.NONE).forGetter(structure -> structure.terrainAdaptation()),
        StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.getStartPool()),
        Identifier.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(structure -> structure.startJigsawName),
        Codec.intRange(0, 20).fieldOf("size").forGetter(structure -> structure.maxDepth),
        HeightProvider.CODEC.fieldOf("start_height").forGetter(structure -> structure.startHeight),
        Codec.BOOL.fieldOf("use_expansion_hack").forGetter(structure -> structure.useExpansionHack),
        Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(structure -> structure.projectStartToHeightmap),
        Codec.BOOL.fieldOf("project_start_to_surface").forGetter(structure -> structure.projectStartToSurface),
        MaxDistance.CODEC.fieldOf("max_distance_from_center").forGetter(structure -> structure.maxDistanceFromCenter),
        Codec.list(PoolAliasBinding.CODEC).optionalFieldOf("pool_aliases", List.of()).forGetter(structure -> structure.poolAliases),
        DimensionPadding.CODEC.optionalFieldOf("dimension_padding", DEFAULT_DIMENSION_PADDING).forGetter(structure -> structure.dimensionPadding),
        LiquidSettings.CODEC.optionalFieldOf("liquid_settings", DEFAULT_LIQUID_SETTINGS).forGetter(structure -> structure.liquidSettings)
    ).apply(instance, MineExitStructure::new)).validate(MineExitStructure::verifyRange);

    public final CraftmineExitType requiredExitType;
    public final boolean projectStartToSurface;

    public MineExitStructure(Map<MobCategory, StructureSpawnOverride> spawnOverrides, CraftmineExitType requiredExitType, GenerationStep.Decoration step, TerrainAdjustment terrainAdaptation, Holder<StructureTemplatePool> startPool, Optional<Identifier> startJigsawName, int maxDepth, HeightProvider startHeight, boolean useExpansionHack, Optional<Heightmap.Types> projectStartToHeightmap, boolean projectStartToSurface, MaxDistance maxDistanceFromCenter, List<PoolAliasBinding> poolAliases, DimensionPadding dimensionPadding, LiquidSettings liquidSettings) {
        // The vanilla StructureSettings requires a biomes holder set; ours is
        // never consulted because biomes() below is overridden. Keep it empty.
        super(new Structure.StructureSettings(HolderSet.direct(List.of()), spawnOverrides, step, terrainAdaptation), startPool, startJigsawName, maxDepth, startHeight, useExpansionHack, projectStartToHeightmap, maxDistanceFromCenter, poolAliases, dimensionPadding, liquidSettings);
        this.requiredExitType = requiredExitType;
        this.projectStartToSurface = projectStartToSurface;
    }

    private static DataResult<MineExitStructure> verifyRange(MineExitStructure structure) {
        int edgeNeeded = structure.terrainAdaptation() == TerrainAdjustment.NONE ? 0 : 12;
        if (structure.maxDistanceFromCenter.horizontal() + edgeNeeded > MAX_TOTAL_STRUCTURE_RANGE) {
            return DataResult.error(() -> "Horizontal structure size including terrain adaptation must not exceed 128");
        }
        return DataResult.success(structure);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int height = this.startHeight.sample(context.random(), new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()));
        BlockPos startPos = new BlockPos(chunkPos.getMinBlockX(), height, chunkPos.getMinBlockZ());
        if (this.projectStartToSurface) {
            startPos = MineExitStructure.projectToSurface(context, startPos);
        }
        return JigsawPlacement.addPieces(context, this.getStartPool(), this.startJigsawName, this.maxDepth, startPos, this.useExpansionHack, this.projectStartToHeightmap, this.maxDistanceFromCenter, PoolAliasLookup.create(this.poolAliases, startPos, context.seed()), this.dimensionPadding, this.liquidSettings);
    }

    /**
     * Fork ewy.a 1:1: scan the noise column for the first block that is solid
     * with air (or a waterloggable block) above - down first, then back up from
     * the original height, then keep the original height. No RNG consumed, so
     * the level's structure RNG stream stays fork-identical.
     */
    private static BlockPos projectToSurface(Structure.GenerationContext context, BlockPos pos) {
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        LevelHeightAccessor heightAccessor = context.heightAccessor();
        NoiseColumn noiseColumn = chunkGenerator.getBaseColumn(pos.getX(), pos.getZ(), heightAccessor, context.randomState());
        int minY = heightAccessor.getMinY();
        int maxY = heightAccessor.getMaxY();
        int originalY = pos.getY();
        int y = originalY;
        while (y > minY + 5 && !MineExitStructure.isSurfaceY(noiseColumn, y)) {
            --y;
        }
        if (!MineExitStructure.isSurfaceY(noiseColumn, y)) {
            int y2;
            for (y2 = originalY; y2 < maxY && !MineExitStructure.isSurfaceY(noiseColumn, y2); ++y2) {
            }
            if (!MineExitStructure.isSurfaceY(noiseColumn, y2)) {
                y2 = originalY;
            }
            y = y2;
        }
        return pos.atY(y);
    }

    private static boolean isSurfaceY(NoiseColumn noiseColumn, int y) {
        BlockState blockState = noiseColumn.getBlock(y);
        BlockState blockState2 = noiseColumn.getBlock(y + 1);
        return !blockState.isAir() && !blockState.hasProperty(BlockStateProperties.WATERLOGGED) && (blockState2.isAir() || blockState2.hasProperty(BlockStateProperties.WATERLOGGED));
    }

    /**
     * Fork evu.a(jl) 26.3 equivalent: the placement-time biome test is the
     * biome's craftmine exit type. stream() stays empty - the ChunkGenerator
     * StructureState stream() consumers are re-routed by the mixin (a dynamic
     * set has no holder universe of its own).
     */
    public CraftmineExitType requiredExitType() {
        return this.requiredExitType;
    }

    @Override
    public HolderSet<Biome> biomes() {
        CraftmineExitType required = this.requiredExitType;
        return new HolderSet<Biome>(){

            @Override
            public Stream<Holder<Biome>> stream() {
                return Stream.empty();
            }

            @Override
            public java.util.Iterator<Holder<Biome>> iterator() {
                return List.<Holder<Biome>>of().iterator();
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public boolean isBound() {
                return true;
            }

            @Override
            public Either<TagKey<Biome>, List<Holder<Biome>>> unwrap() {
                return Either.right(List.of());
            }

            @Override
            public Optional<Holder<Biome>> getRandomElement(RandomSource randomSource) {
                return Optional.empty();
            }

            @Override
            public Holder<Biome> get(int n) {
                throw new IndexOutOfBoundsException("mine exit biome set is dynamic");
            }

            @Override
            public boolean contains(Holder<Biome> holder) {
                return CraftmineBiomeExitTypes.getFor(holder) == required;
            }

            @Override
            public boolean canSerializeIn(HolderOwner<Biome> holderOwner) {
                return false;
            }

            @Override
            public Optional<TagKey<Biome>> unwrapKey() {
                return Optional.empty();
            }
        };
    }

    @Override
    public StructureType<?> type() {
        return CraftmineContent.MINE_EXIT;
    }
}
