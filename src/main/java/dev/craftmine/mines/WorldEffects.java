package dev.craftmine.mines;

import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineRegistries;
import dev.craftmine.registry.CraftmineDimensionTypes;
import dev.craftmine.server.CraftmineServerHooks;
import dev.craftmine.duck.CraftmineLevelDuck;
import com.mojang.datafixers.util.Function4;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.EndPlacements;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3f;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import dev.craftmine.item.component.WorldModifiers;
import net.minecraft.world.level.gamerules.GameRules;
import dev.craftmine.unlock.UnlockCondition;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import dev.craftmine.worldgen.GridChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import org.apache.commons.lang3.function.TriFunction;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.util.valueproviders.UniformInt;
import dev.craftmine.server.CraftmineTime;
import dev.craftmine.level.biome.CraftmineExitType;

/**
 * The catalogue of every world effect in the game: world types, exit behaviors, biome
 * selections, passive/hostile mob sets, base stones, challenges and the boss-fight
 * wirings. Each constant registers a {@link WorldEffect} through its builder at class
 * init (driven from the registry bootstrap), so ordering inside this file defines both
 * unlock-tree order and the "first N unlocked" counter order — do not reorder lightly.
 *
 * <p>Helpers at the bottom build the ingredient item stacks and the exclusive/at-least-one
 * effect sets the Mine Crafter rolls against.</p>
 */
public final class WorldEffects {
    public static final float NON_UNLOCKED_RANDOM_EFFECTS_RARITY_FACTOR = 0.1f;
    public static final WorldEffectSet WORLD_TYPE = WorldEffects.mustHaveOnlyOne("world_type");
    public static final WorldEffect SURFACE_WORLD = WorldEffect.builder("surface_world").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.setNoiseGenerationBase(NoiseGeneratorSettings.OVERWORLD)).withCustomIcon("surface_world").inSet(WORLD_TYPE).unlockedByDefault().register();
    public static final WorldEffect FLOATING_ISLANDS_WORLD = WorldEffect.builder("floating_islands_world").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.setNoiseGenerationBase(NoiseGeneratorSettings.FLOATING_ISLANDS)).withCustomIcon("floating_islands_world").inSet(WORLD_TYPE).unlockedBy(UnlockCondition.mineWon((serverLevel, serverPlayer) -> true)).register();
    public static final WorldEffect AMPLIFIED = WorldEffect.builder("amplified").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.setNoiseGenerationBase(NoiseGeneratorSettings.AMPLIFIED)).inSet(WORLD_TYPE).withCustomIcon("amplified").unlockedAfter(FLOATING_ISLANDS_WORLD).unlockedBy(UnlockCondition.blockPlace((TriFunction<ServerLevel, BlockState, BlockPos, Boolean>)((TriFunction<ServerLevel, BlockState, BlockPos, Boolean>)(ServerLevel serverLevel, BlockState blockState, BlockPos blockPos) -> blockPos.getY() >= 100))).register();
    public static final WorldEffect CAVE_WORLD = WorldEffect.builder("cave_world").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.setNoiseGenerationBase(NoiseGeneratorSettings.CAVES).withSpawnStrategy(MineSpawnStrategy.CAVE).changeDimensionType(CraftmineDimensionTypes::withAmbientLight)).withCustomIcon("cave_world").inSet(WORLD_TYPE).unlockedAfter(FLOATING_ISLANDS_WORLD).unlockedBy(UnlockCondition.blockBreak((TriFunction<ServerLevel, BlockState, BlockPos, Boolean>)((TriFunction<ServerLevel, BlockState, BlockPos, Boolean>)(ServerLevel serverLevel, BlockState blockState, BlockPos blockPos) -> blockPos.getY() < 0))).register();
    public static final WorldEffect SHATTERED_BLOCKS_WORLD = WorldEffect.builder("shattered_blocks_world").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.noiseRouter(dev.craftmine.worldgen.ShatteredNoiseRouter.create(worldGenBuilder.registries().lookupOrThrow(Registries.DENSITY_FUNCTION), worldGenBuilder.registries().lookupOrThrow(Registries.NOISE), builder.noiseRouter())))).withCustomIcon("shattered_blocks").inSet(WORLD_TYPE).unlockedAfter(AMPLIFIED, CAVE_WORLD).unlockedBy(UnlockCondition.specialCompleted(true)).register();
    public static final WorldEffect GRID_WORLD = WorldEffect.builder("grid_world").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.withCustomChunkGenerator((provider, biomeSource, holder, craftSeed) -> {
        // Task 38 (fork fcd.h 1:1): the fork seeded the layout RNG with the
        // NoiseGeneratorSettings' seed field (set to the WorldGenBuilder's identity
        // hash at craft time and read back via ekz.p()); the port receives the same
        // value as craftSeed (26.3's settings record has no seed field). Previously
        // this was newInstance(0L) -> every crafted grid mine had identical
        // spacing/width/y. spacing/width/y persist in GridChunkGenerator's codec,
        // so the layout stays stable per mine across restarts.
        RandomSource randomSource = holder.value().getRandomSource().newInstance(craftSeed);
        int n = 2 << randomSource.nextInt(3);
        int n2 = randomSource.nextIntBetweenInclusive(1, Math.min(n - 1, 3));
        int n3 = randomSource.nextIntBetweenInclusive(0, 64);
        return new GridChunkGenerator(biomeSource, holder, n, n2, n3, true);
    })).withCustomIcon("grid_world").inSet(WORLD_TYPE).unlockedAfter(SHATTERED_BLOCKS_WORLD).unlockedBy(UnlockCondition.specialCompleted(true)).register();
    public static final WorldEffect DARK_CAVE_WORLD = WorldEffect.builder("dark_cave_world").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.setNoiseGenerationBase(NoiseGeneratorSettings.CAVES).withSpawnStrategy(MineSpawnStrategy.CAVE)).withCustomIcon("dark_cave_world").inSet(WORLD_TYPE).unlockedAfter(GRID_WORLD).unlockedBy(UnlockCondition.specialCompleted(true)).register();
    
    // ---- Exit behaviors (where the mine exit generates) ----
public static final WorldEffectSet EXITS = WorldEffects.mustHaveOnlyOne("exits");
    public static final WorldEffect SURFACE_EXITS = WorldEffect.builder("surface_exits").withCustomIcon("surface_exits").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeBiomes(biomeModificationBuilder -> biomeModificationBuilder.modifyExitType(CraftmineExitType.SURFACE))).inSet(EXITS).incompatibleWith(CAVE_WORLD, DARK_CAVE_WORLD).unlockedByDefault().register();
    public static final WorldEffect CAVE_EXITS = WorldEffect.builder("cave_exits").withCustomIcon("cave_exits").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeBiomes(biomeModificationBuilder -> biomeModificationBuilder.modifyExitType(CraftmineExitType.CAVE))).inSet(EXITS).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> blockState.is(Blocks.DEEPSLATE) && serverLevel.getRandom().nextFloat() < 0.01f)).unlockedBy(UnlockCondition.unlocked(CAVE_WORLD)).register();
    public static final WorldEffect RARE_SURFACE_EXITS = WorldEffect.builder("rare_surface_exits").withCustomIcon("rare_surface_exits").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeBiomes(biomeModificationBuilder -> biomeModificationBuilder.modifyExitType(CraftmineExitType.RARE_SURFACE))).inSet(EXITS).incompatibleWith(CAVE_WORLD, DARK_CAVE_WORLD).unlockedBy(UnlockCondition.craftedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(CraftmineContent.EXIT_EYE)))).register();
    public static final WorldEffect EVENT_EXIT = WorldEffect.builder("event_exit").notRandomizable().neverUnlocked().register();
    
    // ---- Biome selections (at least one per mine) ----
public static final WorldEffectSet BIOMES = WorldEffects.mustHaveAtLeastOne("biome");
    public static final WorldEffect PLAINS = WorldEffect.builder("plains").withItemModelOf(Items.GRASS_BLOCK).addBiomes(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW).unlockedByDefault().inSet(BIOMES).register();
    public static final WorldEffect SAVANNAS = WorldEffect.builder("savannas").withItemModelOf(Items.ACACIA_SAPLING).addBiomes(Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA).unlockedByDefault().inSet(BIOMES).register();
    public static final WorldEffect FORESTS = WorldEffect.builder("forests").withItemModelOf(Items.OAK_SAPLING).addBiomes(Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.FLOWER_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.GROVE, Biomes.CHERRY_GROVE, Biomes.DAPPLED_FOREST).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(BlockTags.LEAVES) && serverLevel.getRandom().nextFloat() < 0.01f)).unlockedByWinning().inSet(BIOMES).register();
    public static final WorldEffect TAIGAS = WorldEffect.builder("taigas").withItemModelOf(Items.SPRUCE_SAPLING).addBiomes(Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(BlockTags.LEAVES) && serverLevel.getRandom().nextFloat() < 0.01f)).unlockedByWinning().inSet(BIOMES).register();
    public static final WorldEffect JUNGLES = WorldEffect.builder("jungles").withItemModelOf(Items.JUNGLE_SAPLING).addBiomes(Biomes.JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.SPARSE_JUNGLE).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(BlockTags.LEAVES) && serverLevel.getRandom().nextFloat() < 0.01f)).unlockedByWinning().inSet(BIOMES).register();
    public static final WorldEffect SNOWY = WorldEffect.builder("snowy").withItemModelOf(Items.SNOWBALL).addBiomes(Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.SNOWY_SLOPES, Biomes.SNOWY_BEACH, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.FROZEN_PEAKS, Biomes.FROZEN_RIVER).inSet(BIOMES).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> (blockState.is(BlockTags.SNOW) || blockState.is(BlockTags.ICE)) && serverLevel.getRandom().nextFloat() < 0.05f)).unlockedByWinning().register();
    public static final WorldEffect DESERT = WorldEffect.builder("desert").withItemModelOf(Items.SAND).addBiomes(Biomes.DESERT, Biomes.SULFUR_CAVES).inSet(BIOMES).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> blockState.is(BlockTags.SAND) && serverLevel.getRandom().nextFloat() < 0.01f)).unlockedByWinning().register();
    public static final WorldEffect BADLANDS = WorldEffect.builder("badlands").withItemModelOf(Items.DYED_TERRACOTTA.pick(DyeColor.GRAY)).addBiomes(Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS, Biomes.DRIPSTONE_CAVES).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> (blockState.is(BlockTags.TERRACOTTA) || blockState.is(Blocks.CLAY)) && serverLevel.getRandom().nextFloat() < 0.05f)).unlockedByWinning().inSet(BIOMES).register();
    public static final WorldEffect SWAMPS = WorldEffect.builder("swamps").withItemModelOf(Items.MANGROVE_ROOTS).addBiomes(Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.LUSH_CAVES).inSet(BIOMES).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> blockState.is(Blocks.FIREFLY_BUSH))).unlockedByWinning().register();
    public static final WorldEffect DARK_FORESTS = WorldEffect.builder("dark_forests").withItemModelOf(Items.CREAKING_HEART).addBiomes(Biomes.DARK_FOREST, Biomes.PALE_GARDEN).inSet(BIOMES).unlockedAfter(FORESTS, JUNGLES, TAIGAS).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(BlockTags.LEAVES) && serverLevel.getRandom().nextFloat() < 0.01f)).unlockedByWinning().register();
    public static final WorldEffect ICE_SPIKES = WorldEffect.builder("ice_spikes").withItemModelOf(Items.PACKED_ICE).addBiomes(Biomes.ICE_SPIKES).inSet(BIOMES).unlockedBy(UnlockCondition.mineCompletedWith(true, SNOWY)).unlockedByWinning().register();
    public static final WorldEffect MUSHROOM_FIELDS = WorldEffect.builder("mushroom_fields").withItemModelOf(Items.RED_MUSHROOM).addBiomes(Biomes.MUSHROOM_FIELDS).inSet(BIOMES).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> (blockState.is(Blocks.BROWN_MUSHROOM) || blockState.is(Blocks.RED_MUSHROOM)) && serverLevel.getRandom().nextFloat() < 0.1f)).unlockedByWinning().register();
    public static final WorldEffect NETHER_BARRENS = WorldEffect.builder("nether_barrens").withItemModelOf(Items.NETHERRACK).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.seaLevel(builder.seaLevel() - 64))).addBiomes(Biomes.NETHER_WASTES, Biomes.SOUL_SAND_VALLEY, Biomes.BASALT_DELTAS).inSet(BIOMES).unlockedBy(UnlockCondition.obtainedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(Items.OBSIDIAN)))).register();
    public static final WorldEffect NETHER_FORESTS = WorldEffect.builder("nether_forests").withItemModelOf(Items.WARPED_FUNGUS).addBiomes(Biomes.CRIMSON_FOREST, Biomes.WARPED_FOREST).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.seaLevel(builder.seaLevel() - 64))).inSet(BIOMES).unlockedBy(UnlockCondition.playerKilledEntity(0.2f, EntityTypes.ENDERMAN)).register();
    public static final WorldEffect DEEP_DARK = WorldEffect.builder("deep_dark").withItemModelOf(Items.SCULK).inSet(BIOMES).addBiomes(Biomes.DEEP_DARK).unlockedBy(UnlockCondition.blockBreak((TriFunction<ServerLevel, BlockState, BlockPos, Boolean>)((TriFunction<ServerLevel, BlockState, BlockPos, Boolean>)(ServerLevel serverLevel, BlockState blockState, BlockPos blockPos) -> serverLevel.getRandom().nextFloat() < 0.1f && WorldEffects.isDarkPlace(serverLevel, blockPos)))).register();
    public static final WorldEffect END = WorldEffect.builder("end").withItemModelOf(Items.END_STONE).inSet(BIOMES).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.seaLevel(builder.seaLevel() - 64))).addBiomes(Biomes.THE_END, Biomes.END_HIGHLANDS, Biomes.END_BARRENS, Biomes.END_MIDLANDS, Biomes.SMALL_END_ISLANDS).unlockedBy(UnlockCondition.playerKilledEntity(0.5f, EntityTypes.BLAZE)).register();
    public static final WorldEffect DRY_LAND = WorldEffect.builder("dry_land").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.seaLevel(-64))).unlockedBy(UnlockCondition.obtainedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> serverLevel.getBiome(serverPlayer.blockPosition()).is(BiomeTags.IS_BADLANDS) && itemStack.is(Items.LAVA_BUCKET)))).withItemModelOf(Items.BUCKET).register();
    public static final WorldEffect WATER_WORLD = WorldEffect.builder("water_world").modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.seaLevel(128))).incompatibleWith(DRY_LAND).unlockedBy(UnlockCondition.mineCompletedWith(true, DRY_LAND)).withItemModelOf(Items.WATER_BUCKET).register();
    
    // ---- Passive mob sets ----
public static final WorldEffectSet PASSIVE_MOBS = WorldEffects.mustHaveAtLeastOne("passive_mobs");
    public static final Style PASSIVE_MOBS_STYLE = Style.EMPTY.withColor(ChatFormatting.DARK_GREEN);
    public static final WorldEffect SHEEP = WorldEffect.builder("sheep").withNameStyle(PASSIVE_MOBS_STYLE).unlockedByDefault().withItemModelOf(Items.SHEEP_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 12, new MobSpawnSettings.SpawnerData(EntityTypes.SHEEP, UniformInt.of(4, 4)))).register();
    public static final WorldEffect COWS = WorldEffect.builder("cows").withNameStyle(PASSIVE_MOBS_STYLE).unlockedByDefault().withItemModelOf(Items.COW_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityTypes.COW, UniformInt.of(4, 4)))).register();
    public static final WorldEffect PIGS = WorldEffect.builder("pigs").withNameStyle(PASSIVE_MOBS_STYLE).unlockedByDefault().withItemModelOf(Items.PIG_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 10, new MobSpawnSettings.SpawnerData(EntityTypes.PIG, UniformInt.of(4, 4)))).register();
    public static final WorldEffect CHICKENS = WorldEffect.builder("chickens").withNameStyle(PASSIVE_MOBS_STYLE).unlockedByDefault().withItemModelOf(Items.CHICKEN_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 10, new MobSpawnSettings.SpawnerData(EntityTypes.CHICKEN, UniformInt.of(4, 4)))).register();
    public static final WorldEffect FROGS = WorldEffect.builder("frogs").withNameStyle(PASSIVE_MOBS_STYLE).unlockedByDefault().withItemModelOf(Items.FROG_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 10, new MobSpawnSettings.SpawnerData(EntityTypes.FROG, UniformInt.of(4, 4)))).register();
    public static final WorldEffect FOXES = WorldEffect.builder("foxes").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.obtainedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(Items.SWEET_BERRIES)))).withItemModelOf(Items.FOX_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 10, new MobSpawnSettings.SpawnerData(EntityTypes.FOX, UniformInt.of(1, 3)))).register();
    public static final WorldEffect GOATS = WorldEffect.builder("goats").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.blockBreak((TriFunction<ServerLevel, BlockState, BlockPos, Boolean>)((TriFunction<ServerLevel, BlockState, BlockPos, Boolean>)(ServerLevel serverLevel, BlockState blockState, BlockPos blockPos) -> blockPos.getY() > 128 && (double)serverLevel.getRandom().nextFloat() < 0.1))).withItemModelOf(Items.GOAT_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 10, new MobSpawnSettings.SpawnerData(EntityTypes.GOAT, UniformInt.of(1, 3)))).register();
    public static final WorldEffect OCELOTS = WorldEffect.builder("ocelots").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.obtainedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(Items.STRING)))).alwaysRandomizable().withItemModelOf(Items.OCELOT_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 20, new MobSpawnSettings.SpawnerData(EntityTypes.OCELOT, UniformInt.of(15, 15)))).register();
    public static final WorldEffect AXOLOTLS = WorldEffect.builder("axolotls").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.obtainedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(Items.AXOLOTL_BUCKET)))).alwaysRandomizable().withItemModelOf(Items.AXOLOTL_SPAWN_EGG).inSet(PASSIVE_MOBS).addBiomes(Biomes.LUSH_CAVES).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.AXOLOTLS, 20, new MobSpawnSettings.SpawnerData(EntityTypes.AXOLOTL, UniformInt.of(15, 15)))).incompatibleWith(DRY_LAND).register();
    public static final WorldEffect ARMADILLOS = WorldEffect.builder("armadillos").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.craftedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(Items.BRUSH)))).withItemModelOf(Items.ARMADILLO_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 20, new MobSpawnSettings.SpawnerData(EntityTypes.ARMADILLO, UniformInt.of(1, 3)))).register();
    public static final WorldEffect MOOSHROOMS = WorldEffect.builder("mooshrooms").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> (double)serverLevel.getRandom().nextFloat() < 0.1 && (blockState.is(Blocks.RED_MUSHROOM) || blockState.is(Blocks.BROWN_MUSHROOM)))).withItemModelOf(Items.MOOSHROOM_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 20, new MobSpawnSettings.SpawnerData(EntityTypes.MOOSHROOM, UniformInt.of(1, 3)))).register();
    public static final WorldEffect PANDAS = WorldEffect.builder("pandas").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.usedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(Items.BAMBOO)))).withItemModelOf(Items.PANDA_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 100, new MobSpawnSettings.SpawnerData(EntityTypes.PANDA, UniformInt.of(3, 5)))).register();
    public static final WorldEffect PARROTS = WorldEffect.builder("parrots").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.usedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(Items.COOKIE)))).withItemModelOf(Items.PARROT_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 100, new MobSpawnSettings.SpawnerData(EntityTypes.PARROT, UniformInt.of(3, 5)))).register();
    public static final WorldEffect RABBITS = WorldEffect.builder("rabbits").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.playerTookDamage((Function4<ServerLevel, ServerPlayer, DamageSource, Float, Boolean>)((Function4<ServerLevel, ServerPlayer, DamageSource, Float, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, DamageSource damageSource, Float f) -> damageSource.is(DamageTypes.FALL) && f.floatValue() > 10.0f))).withItemModelOf(Items.RABBIT_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 100, new MobSpawnSettings.SpawnerData(EntityTypes.RABBIT, UniformInt.of(3, 5)))).register();
    // Task 35 (BugMine "Rabbits Spawns Rabbits", always-on): the snapshot
    // copy-pasted the parrots effect and spawns PARROTS for the Rabbits
    // ingredient; it spawns rabbits like the name says.
    public static final WorldEffect SNIFFERS = WorldEffect.builder("sniffers").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(BlockTags.FLOWERS) && serverLevel.getRandom().nextFloat() < 0.05f)).withItemModelOf(Items.SNIFFER_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 10, new MobSpawnSettings.SpawnerData(EntityTypes.SNIFFER, UniformInt.of(1, 2)))).register();
    public static final WorldEffect STRIDERS = WorldEffect.builder("striders").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.obtainedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(Items.SADDLE)))).withItemModelOf(Items.STRIDER_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 25, new MobSpawnSettings.SpawnerData(EntityTypes.STRIDER, UniformInt.of(1, 2)))).register();
    // Task 55 (FT-5): mine ingredients for 26.3's new mobs. The fork predates
    // these mobs; the passive/hostile patterns above are extended fork-style.
    // Native MobCategory is kept (fork precedent: striders=CREATURE,
    // axolotls=AXOLOTLS, guardians=MONSTER). Weights/counts are 26.3 vanilla
    // natural-spawn values where they exist (nautilus 5/(1,1) from the ocean
    // biomes), fork-style picks otherwise (happy ghast big-mount family).
    // Task 56 (user): both passives are now victory-gated instead of default:
    // nautiluses after winning a Water World mine (fork precedent: water_world
    // itself unlocks via mineCompletedWith(true, DRY_LAND)), happy_ghasts after
    // winning the Angry Ghast mine. NAUTILUSES can use the factory directly
    // (WATER_WORLD is declared above). HAPPY_GHASTS needs an inline UnlockCondition
    // because ANGRY_GHAST_BOSS_FIGHT is declared below: a field-initializer
    // reference would be a forward reference (simple name: compile error;
    // qualified name: captures null during <clinit>). The anonymous method body
    // is legal and resolves the field lazily at check time. Semantics are
    // byte-for-byte those of UnlockCondition.mineCompletedWith(true, X).
    public static final WorldEffect HAPPY_GHASTS = WorldEffect.builder("happy_ghasts").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(new UnlockCondition(){
            @Override
            public boolean mapCompleted(ServerLevel serverLevel, ServerPlayer serverPlayer, java.util.Set<WorldEffect> set, boolean bl) {
                return bl && set.contains(WorldEffects.ANGRY_GHAST_BOSS_FIGHT);
            }
        }).withItemModelOf(Items.HAPPY_GHAST_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 10, new MobSpawnSettings.SpawnerData(EntityTypes.HAPPY_GHAST, UniformInt.of(1, 2)))).register();
    public static final WorldEffect NAUTILUSES = WorldEffect.builder("nautiluses").withNameStyle(PASSIVE_MOBS_STYLE).unlockedBy(UnlockCondition.mineCompletedWith(true, WATER_WORLD)).withItemModelOf(Items.NAUTILUS_SPAWN_EGG).inSet(PASSIVE_MOBS).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.WATER_CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityTypes.NAUTILUS, UniformInt.of(1, 1)))).register();
    
    // ---- Hostile mob sets ----
public static final WorldEffectSet HOSTILE_MOBS = WorldEffects.mustHaveAtLeastOne("hostile_mobs");
    public static final Style HOSTILE_MOB_STYLE = Style.EMPTY.withColor(ChatFormatting.RED);
    public static final WorldEffect ZOMBIES = WorldEffect.builder("zombies").inSet(HOSTILE_MOBS).withItemModelOf(Items.ZOMBIE_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> serverLevel.getRandom().nextFloat() < 0.05f && entity.getType().getCategory() == MobCategory.MONSTER))).unlockedByWinning().alwaysRandomizable().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 95, new MobSpawnSettings.SpawnerData(EntityTypes.ZOMBIE, UniformInt.of(4, 4)))).register();
    public static final WorldEffect SKELETONS = WorldEffect.builder("skeletons").inSet(HOSTILE_MOBS).withItemModelOf(Items.SKELETON_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> serverLevel.getRandom().nextFloat() < 0.05f && entity.getType().getCategory() == MobCategory.MONSTER))).unlockedByWinning().alwaysRandomizable().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 100, new MobSpawnSettings.SpawnerData(EntityTypes.SKELETON, UniformInt.of(4, 4)))).register();
    public static final WorldEffect SPIDERS = WorldEffect.builder("spiders").inSet(HOSTILE_MOBS).withItemModelOf(Items.SPIDER_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> serverLevel.getRandom().nextFloat() < 0.05f && entity.getType().getCategory() == MobCategory.MONSTER))).unlockedByWinning().alwaysRandomizable().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 100, new MobSpawnSettings.SpawnerData(EntityTypes.SPIDER, UniformInt.of(4, 4)))).register();
    public static final WorldEffect CAVE_SPIDERS = WorldEffect.builder("cave_spiders").inSet(HOSTILE_MOBS).withItemModelOf(Items.CAVE_SPIDER_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> serverLevel.getRandom().nextFloat() < 0.2f && entity instanceof Spider))).unlockedByWinning().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 50, new MobSpawnSettings.SpawnerData(EntityTypes.CAVE_SPIDER, UniformInt.of(4, 4)))).register();
    public static final WorldEffect CREEPERS = WorldEffect.builder("creepers").inSet(HOSTILE_MOBS).withItemModelOf(Items.CREEPER_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> serverLevel.getRandom().nextFloat() < 0.05f && entity.getType().getCategory() == MobCategory.MONSTER))).unlockedByWinning().alwaysRandomizable().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 100, new MobSpawnSettings.SpawnerData(EntityTypes.CREEPER, UniformInt.of(4, 4)))).register();
    public static final WorldEffect SLIMES = WorldEffect.builder("slimes").inSet(HOSTILE_MOBS).withItemModelOf(Items.SLIME_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> serverLevel.getRandom().nextFloat() < 0.05f && entity.getType().getCategory() == MobCategory.MONSTER))).unlockedByWinning().alwaysRandomizable().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 100, new MobSpawnSettings.SpawnerData(EntityTypes.SLIME, UniformInt.of(4, 4)))).register();
    public static final WorldEffect ENDERMEN = WorldEffect.builder("endermen").inSet(HOSTILE_MOBS).withItemModelOf(Items.ENDERMAN_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedAfter(ZOMBIES, SKELETONS, CREEPERS, SPIDERS).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> serverLevel.getRandom().nextFloat() < 0.05f && entity.getType().getCategory() == MobCategory.MONSTER))).unlockedByWinning().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 10, new MobSpawnSettings.SpawnerData(EntityTypes.ENDERMAN, UniformInt.of(1, 4)))).register();
    public static final WorldEffect WITCHES = WorldEffect.builder("witches").inSet(HOSTILE_MOBS).withItemModelOf(Items.WITCH_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedAfter(ZOMBIES, SKELETONS, CREEPERS, SPIDERS).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> serverLevel.getRandom().nextFloat() < 0.05f && entity.getType().getCategory() == MobCategory.MONSTER))).unlockedByWinning().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 5, new MobSpawnSettings.SpawnerData(EntityTypes.WITCH, UniformInt.of(1, 1)))).register();
    public static final WorldEffect MAGMA_CUBES = WorldEffect.builder("magma_cubes").inSet(HOSTILE_MOBS).withItemModelOf(Items.MAGMA_CUBE_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy(UnlockCondition.obtainedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(Items.MAGMA_CREAM) || itemStack.is(Items.MAGMA_BLOCK)))).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 100, new MobSpawnSettings.SpawnerData(EntityTypes.MAGMA_CUBE, UniformInt.of(2, 5)))).register();
    public static final WorldEffect BLAZES = WorldEffect.builder("blazes").inSet(HOSTILE_MOBS).withItemModelOf(Items.BLAZE_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> blockState.is(Blocks.NETHER_QUARTZ_ORE) || blockState.is(Blocks.NETHER_GOLD_ORE))).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 10, new MobSpawnSettings.SpawnerData(EntityTypes.BLAZE, UniformInt.of(2, 3)))).register();
    public static final WorldEffect BREEZES = WorldEffect.builder("breezes").inSet(HOSTILE_MOBS).withItemModelOf(Items.BREEZE_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy(UnlockCondition.obtainedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(Items.TRIAL_KEY)))).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 10, new MobSpawnSettings.SpawnerData(EntityTypes.BREEZE, UniformInt.of(2, 3)))).register();
    public static final WorldEffect PILLAGERS = WorldEffect.builder("pillagers").inSet(HOSTILE_MOBS).withItemModelOf(Items.PILLAGER_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 10, new MobSpawnSettings.SpawnerData(EntityTypes.PILLAGER, UniformInt.of(1, 5)))).register();
    public static final WorldEffect VINDICATORS = WorldEffect.builder("vindicators").inSet(HOSTILE_MOBS).withItemModelOf(Items.VINDICATOR_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 10, new MobSpawnSettings.SpawnerData(EntityTypes.VINDICATOR, UniformInt.of(1, 1)))).register();
    public static final WorldEffect EVOKERS = WorldEffect.builder("evokers").inSet(HOSTILE_MOBS).withItemModelOf(Items.EVOKER_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 10, new MobSpawnSettings.SpawnerData(EntityTypes.EVOKER, UniformInt.of(1, 1)))).register();
    public static final WorldEffect RAVAGERS = WorldEffect.builder("ravagers").inSet(HOSTILE_MOBS).withItemModelOf(Items.RAVAGER_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).unlockedBy().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 1, new MobSpawnSettings.SpawnerData(EntityTypes.RAVAGER, UniformInt.of(1, 1)))).register();
    public static final WorldEffect ILLUSIONERS = WorldEffect.builder("illusioners").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withCustomIcon("illusioners").unlockedBy().modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 1, new MobSpawnSettings.SpawnerData(EntityTypes.ILLUSIONER, UniformInt.of(1, 1)))).register();
    public static final WorldEffect GUARDIANS = WorldEffect.builder("guardians").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.GUARDIAN_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> entity.is(EntityTypeTags.AQUATIC)))).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 10, new MobSpawnSettings.SpawnerData(EntityTypes.GUARDIAN, UniformInt.of(1, 1)))).register();
    public static final WorldEffect ENDERMITES = WorldEffect.builder("endermites").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.ENDERMITE_SPAWN_EGG).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> blockState.is(Blocks.END_STONE) && (double)serverLevel.getRandom().nextFloat() < 0.1)).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 100, new MobSpawnSettings.SpawnerData(EntityTypes.ENDERMITE, UniformInt.of(2, 4)))).register();
    public static final WorldEffect SHULKERS = WorldEffect.builder("shulkers").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.SHULKER_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity(0.1f, EntityTypes.ENDERMITE)).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 50, new MobSpawnSettings.SpawnerData(EntityTypes.SHULKER, UniformInt.of(1, 1)))).register();
    public static final WorldEffect GHASTS = WorldEffect.builder("ghasts").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.GHAST_SPAWN_EGG).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> blockState.is(Blocks.BONE_BLOCK))).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 50, new MobSpawnSettings.SpawnerData(EntityTypes.GHAST, UniformInt.of(1, 1)))).register();
    public static final WorldEffect ZOMBIFIED_PIGLINS = WorldEffect.builder("zombified_piglins").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.ZOMBIFIED_PIGLIN_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) ->
        entity instanceof Pig pig && pig.isOnFire()))).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 100, new MobSpawnSettings.SpawnerData(EntityTypes.ZOMBIFIED_PIGLIN, UniformInt.of(4, 4)))).register();
    public static final WorldEffect PIGLINS = WorldEffect.builder("piglins").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.PIGLIN_SPAWN_EGG).unlockedBy(UnlockCondition.fedAnimal((Function4<ServerLevel, ServerPlayer, Animal, ItemStack, Boolean>)((Function4<ServerLevel, ServerPlayer, Animal, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Animal animal, ItemStack itemStack) ->
        animal instanceof Pig && itemStack.is(Items.GOLDEN_CARROT)))).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 100, new MobSpawnSettings.SpawnerData(EntityTypes.PIGLIN, UniformInt.of(4, 4))).addMobSpawn(MobCategory.MONSTER, 2, new MobSpawnSettings.SpawnerData(EntityTypes.PIGLIN_BRUTE, UniformInt.of(1, 1))).changeDimensionType(CraftmineDimensionTypes::asPiglinSafe)).register();
    public static final WorldEffect WITHER_SKELETONS = WorldEffect.builder("wither_skeletons").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.WITHER_SKELETON_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity(0.1f, EntityTypes.SKELETON)).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 25, new MobSpawnSettings.SpawnerData(EntityTypes.WITHER_SKELETON, UniformInt.of(1, 3)))).register();
    // Task 35 (BugMine "Wither Skeletons Not Zombified Piglins", always-on):
    // the ingredient icon was a Zombified Piglin spawn egg; it is now the
    // Wither Skeleton spawn egg it should be.
    public static final WorldEffect BEES = WorldEffect.builder("bees").inSet(HOSTILE_MOBS).withNameStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)).withItemModelOf(Items.BEE_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> entity instanceof Bee))).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.CREATURE, 250, new MobSpawnSettings.SpawnerData(EntityTypes.BEE, UniformInt.of(5, 5)))).register();
    public static final WorldEffect HOGLINS = WorldEffect.builder("hoglins").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.HOGLIN_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity(0.01f, EntityTypes.PIG)).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 25, new MobSpawnSettings.SpawnerData(EntityTypes.HOGLIN, UniformInt.of(1, 3))).changeDimensionType(CraftmineDimensionTypes::asPiglinSafe)).register();
    public static final WorldEffect ZOGLINS = WorldEffect.builder("zoglins").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.ZOGLIN_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> {
        Zombie zombie;
        return entity instanceof Zombie && (zombie = (Zombie)entity).getBlockStateOn().is(BlockTags.NYLIUM);
    }))).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 2, new MobSpawnSettings.SpawnerData(EntityTypes.ZOGLIN, UniformInt.of(1, 3)))).register();
    public static final WorldEffect PARCHEDS = WorldEffect.builder("parcheds").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.PARCHED_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity(0.1f, EntityTypes.HUSK)).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 50, new MobSpawnSettings.SpawnerData(EntityTypes.PARCHED, UniformInt.of(4, 4)))).register();
    public static final WorldEffect CAMEL_HUSKS = WorldEffect.builder("camel_husks").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.CAMEL_HUSK_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity(0.1f, EntityTypes.CAMEL)).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 10, new MobSpawnSettings.SpawnerData(EntityTypes.CAMEL_HUSK, UniformInt.of(1, 2)))).register();
    public static final WorldEffect ZOMBIE_HORSES = WorldEffect.builder("zombie_horses").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.ZOMBIE_HORSE_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity(0.05f, EntityTypes.ZOMBIE)).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 5, new MobSpawnSettings.SpawnerData(EntityTypes.ZOMBIE_HORSE, UniformInt.of(1, 1)))).register();
    public static final WorldEffect SULFUR_CUBES = WorldEffect.builder("sulfur_cubes").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.SULFUR_CUBE_SPAWN_EGG).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> (blockState.is(Blocks.SULFUR) || blockState.is(Blocks.POTENT_SULFUR) || blockState.is(Blocks.SULFUR_SPIKE)) && serverLevel.getRandom().nextFloat() < 0.1f)).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 100, new MobSpawnSettings.SpawnerData(EntityTypes.SULFUR_CUBE, UniformInt.of(2, 4)))).register();
    public static final WorldEffect ZOMBIE_NAUTILUSES = WorldEffect.builder("zombie_nautiluses").inSet(HOSTILE_MOBS).withNameStyle(HOSTILE_MOB_STYLE).withItemModelOf(Items.ZOMBIE_NAUTILUS_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity(0.1f, EntityTypes.NAUTILUS)).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.addMobSpawn(MobCategory.MONSTER, 5, new MobSpawnSettings.SpawnerData(EntityTypes.ZOMBIE_NAUTILUS, UniformInt.of(1, 2)))).register();
    public static final WorldEffect ICY = WorldEffect.builder("icy").withNameStyle(Style.EMPTY.withColor(ChatFormatting.BLUE)).withItemModelOf(Items.BLUE_ICE).unlockedBy(UnlockCondition.usedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(ItemTags.BOATS)))).xpModifier(2.0f).register();
    public static final WorldEffect BOUNCY = WorldEffect.builder("bouncy").withNameStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)).withItemModelOf(Items.SLIME_BLOCK).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> entity instanceof Slime))).xpModifier(2.0f).register();
    
    // ---- Base stone variants ----
public static final WorldEffectSet BASE_STONE = WorldEffects.mustHaveOnlyOne("base_stone");
    public static final WorldEffect STONE = WorldEffect.builder("base_stone").inSet(BASE_STONE).withItemModelOf(Items.STONE).unlockedByDefault().register();
    public static final WorldEffect BLACKSTONE = WorldEffect.builder("base_blackstone").inSet(BASE_STONE).withItemModelOf(Items.BLACKSTONE).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.defaultBlock(Blocks.BLACKSTONE.defaultBlockState()))).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(Blocks.BLACKSTONE) && serverLevel.getRandom().nextFloat() < 0.1f)).unlockedByWinning().alwaysRandomizable().register();
    public static final WorldEffect DIORITE = WorldEffect.builder("base_diorite").inSet(BASE_STONE).withItemModelOf(Items.DIORITE).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.defaultBlock(Blocks.DIORITE.defaultBlockState()))).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(Blocks.DIORITE) && serverLevel.getRandom().nextFloat() < 0.1f)).unlockedByWinning().alwaysRandomizable().register();
    public static final WorldEffect ANDESITE = WorldEffect.builder("base_andesite").inSet(BASE_STONE).withItemModelOf(Items.ANDESITE).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.defaultBlock(Blocks.ANDESITE.defaultBlockState()))).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(Blocks.ANDESITE) && serverLevel.getRandom().nextFloat() < 0.1f)).unlockedByWinning().alwaysRandomizable().register();
    public static final WorldEffect GRANITE = WorldEffect.builder("base_granite").inSet(BASE_STONE).withItemModelOf(Items.GRANITE).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.defaultBlock(Blocks.GRANITE.defaultBlockState()))).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(Blocks.GRANITE) && serverLevel.getRandom().nextFloat() < 0.1f)).unlockedByWinning().alwaysRandomizable().register();
    public static final WorldEffect TUFF = WorldEffect.builder("base_tuff").inSet(BASE_STONE).withItemModelOf(Items.TUFF).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.defaultBlock(Blocks.TUFF.defaultBlockState()))).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(Blocks.TUFF) && serverLevel.getRandom().nextFloat() < 0.1f)).unlockedByWinning().alwaysRandomizable().register();
    public static final WorldEffect DEEPSLATE = WorldEffect.builder("base_deepslate").inSet(BASE_STONE).withItemModelOf(Items.DEEPSLATE).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.defaultBlock(Blocks.DEEPSLATE.defaultBlockState()))).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(Blocks.DEEPSLATE) && serverLevel.getRandom().nextFloat() < 0.1f)).unlockedByWinning().alwaysRandomizable().register();
    public static final WorldEffect END_STONE = WorldEffect.builder("base_end_stone").inSet(BASE_STONE).withItemModelOf(Items.END_STONE).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeNoiseGeneration(builder -> builder.defaultBlock(Blocks.END_STONE.defaultBlockState()))).unlockedBy(UnlockCondition.blockDrops((serverLevel, blockState) -> blockState.is(Blocks.END_STONE) && serverLevel.getRandom().nextFloat() < 0.1f)).register();
    
    // ---- Challenges and boss-fight wirings ----
public static final Style CHALLENGE_STYLE = Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE);
    public static final WorldEffect ONE_HP = WorldEffect.builder("one_hp").withNameStyle(CHALLENGE_STYLE).withItemModelOf(Items.POTION).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> serverPlayer.getHealth() <= 1.0f))).xpModifier(4.0f).register();
    public static final WorldEffect WEDNESDAY_FROGS = WorldEffect.builder("wednesday_frogs").withNameStyle(CHALLENGE_STYLE).withItemModelOf(Items.FROG_SPAWN_EGG).unlockedBy(UnlockCondition.playerKilledEntity((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)((TriFunction<ServerLevel, ServerPlayer, Entity, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, Entity entity) -> entity instanceof Frog))).xpModifier(1.2f).register();
    public static final WorldEffect UNIVERSAL_ANGER = WorldEffect.builder("universal_anger").withNameStyle(CHALLENGE_STYLE).withItemModelOf(Items.ROTTEN_FLESH).unlockedBy(UnlockCondition.mineCompletedWith(true, BEES)).xpModifier(2.0f).register();
    public static final WorldEffect SOUL_LINK = WorldEffect.builder("soul_link").withNameStyle(CHALLENGE_STYLE).withItemModelOf(Items.LEAD).unlockedBy(UnlockCondition.mineCompletedWith(true, ONE_HP)).xpModifier(2.0f).multiplayerOnly().register();
    public static final WorldEffect ETERNAL_NIGHT = WorldEffect.builder("eternal_night").withItemModelOf(Items.DYE.pick(DyeColor.BLUE)).withNameStyle(CHALLENGE_STYLE).onMineEnter(serverLevel -> {
        // Target the MINE level: its "generated" dimension type's default clock is the
        // shared minecraft:overworld WorldClock. The old theGame().overworld() call was a
        // silent no-op (the overworld dimension type has no default_clock in 26.3 - see
        // CraftmineTime.setDayTime), so the mine never went dark. The ADVANCE_TIME toggle
        // below freezes the shared clock and (vanilla onGameRuleChange) broadcasts a full
        // clock sync so clients actually render the night.
        CraftmineTime.setDayTime(serverLevel, 18000L);
        CraftmineServerHooks.theGame().getGameRules().set(GameRules.ADVANCE_TIME, false, CraftmineServerHooks.theGame().server());
    }).onMineLeave(serverLevel -> CraftmineServerHooks.theGame().getGameRules().set(GameRules.ADVANCE_TIME, true, CraftmineServerHooks.theGame().server())).incompatibleWith(CAVE_WORLD, DARK_CAVE_WORLD).xpModifier(5.0f).requiresUnlockCount(20).register();
    // Batch AG weather audit: 26.3's ServerLevel.advanceWeatherCycle ramps
    // rainLevel/thunderLevel toward the SHARED WeatherData state (one server-wide
    // SavedData) every tick and the ramp sits OUTSIDE the ADVANCE_WEATHER gamerule
    // gate - the gamerule only freezes the binary weather timers. setRainLevel(1.0f)
    // alone therefore decays back to 0 in 100 ticks (rain for ~5s, then clear).
    // The flip of the shared WeatherData state gives the ramp direction "up": with
    // the gamerule frozen the state never changes and the level sits at full rain.
    // PlayerList.sendLevelInfo sends START_RAINING + rain/thunder levels to every
    // player entering the level, so the teleport into the mine renders immediately.
    // On leave the state is cleared and the HUB's rain/thunder levels are zeroed too
    // - while the shared state was "raining" the empty hub level ramped up alongside
    // the mine, and without the zeroing the player would return to a drizzling hub
    // that takes another ~5s to ramp down.
    public static final WorldEffect ETERNAL_RAIN = WorldEffect.builder("eternal_rain").withItemModelOf(Items.DYE.pick(DyeColor.BLACK)).withNameStyle(CHALLENGE_STYLE).onMineEnter(serverLevel -> {
        serverLevel.setRainLevel(1.0f);
        CraftmineServerHooks.theGame().server().getWeatherData().setRaining(true);
        CraftmineServerHooks.theGame().getGameRules().set(GameRules.ADVANCE_WEATHER, false, CraftmineServerHooks.theGame().server());
    }).onMineLeave(serverLevel -> {
        serverLevel.setRainLevel(0.0f);
        CraftmineServerHooks.theGame().server().getWeatherData().setRaining(false);
        CraftmineServerHooks.theGame().overworld().setRainLevel(0.0f);
        CraftmineServerHooks.theGame().overworld().setThunderLevel(0.0f);
        CraftmineServerHooks.theGame().getGameRules().set(GameRules.ADVANCE_WEATHER, true, CraftmineServerHooks.theGame().server());
    }).unlockedBy(UnlockCondition.mineCompletedWith(true, ETERNAL_NIGHT)).incompatibleWith(CAVE_WORLD, DARK_CAVE_WORLD).xpModifier(2.0f).register();
    public static final WorldEffect ETERNAL_LIGHTNING = WorldEffect.builder("eternal_lightning").withItemModelOf(Items.DYE.pick(DyeColor.YELLOW)).withNameStyle(CHALLENGE_STYLE).onMineEnter(serverLevel -> {
        serverLevel.setThunderLevel(1.0f);
        serverLevel.setRainLevel(1.0f);
        CraftmineServerHooks.theGame().server().getWeatherData().setRaining(true);
        CraftmineServerHooks.theGame().server().getWeatherData().setThundering(true);
        CraftmineServerHooks.theGame().getGameRules().set(GameRules.ADVANCE_WEATHER, false, CraftmineServerHooks.theGame().server());
    }).onMineLeave(serverLevel -> {
        serverLevel.setThunderLevel(0.0f);
        serverLevel.setRainLevel(0.0f);
        CraftmineServerHooks.theGame().server().getWeatherData().setRaining(false);
        CraftmineServerHooks.theGame().server().getWeatherData().setThundering(false);
        CraftmineServerHooks.theGame().overworld().setRainLevel(0.0f);
        CraftmineServerHooks.theGame().overworld().setThunderLevel(0.0f);
        CraftmineServerHooks.theGame().getGameRules().set(GameRules.ADVANCE_WEATHER, true, CraftmineServerHooks.theGame().server());
    }).unlockedBy(UnlockCondition.mineCompletedWith(true, ETERNAL_RAIN)).incompatibleWith(CAVE_WORLD, DARK_CAVE_WORLD).xpModifier(5.0f).register();
    public static final WorldEffect INSOMIAC = WorldEffect.builder("insomniacs").withItemModelOf(Items.PHANTOM_SPAWN_EGG).withNameStyle(HOSTILE_MOB_STYLE).onPlayerMineEnter(serverPlayer -> serverPlayer.getStats().setValue((Player)serverPlayer, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 72000)).unlockedBy(UnlockCondition.mineCompletedWith(true, ETERNAL_NIGHT)).incompatibleWith(CAVE_WORLD, DARK_CAVE_WORLD).xpModifier(2.0f).register();
    public static final WorldEffect NO_DROPS = WorldEffect.builder("no_drops").withNameStyle(CHALLENGE_STYLE).withItemModelOf(Items.DIRT).requiresUnlockCount(20).xpModifier(5.0f).unlockedBy(UnlockCondition.playerKilledEntity((ServerLevel serverLevel, ServerPlayer serverPlayer, net.minecraft.world.entity.Entity entity) -> serverLevel.getRandom().nextFloat() < 0.05f)).register();
    // Task 35 (BugMine "Obtainable No Drops", always-on): the ingredient had
    // NO unlock condition at all, making it unobtainable. Restored the
    // original design per the Mojang dev quote: 5% chance per mob kill.
    public static final WorldEffect ULTRAWARM = WorldEffect.builder("ultrawarm").withNameStyle(CHALLENGE_STYLE).withItemModelOf(Items.LAVA_BUCKET).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeDimensionType(WorldEffects::craftmine$madeUltrawarm).changeBiomes(biomeModificationBuilder -> biomeModificationBuilder.modifyClimate(builder -> builder.temperature(builder.temperature() + 1.0f)).modifyAttributes(attrs -> attrs.set(EnvironmentAttributes.SKY_COLOR, new Vector3f(234 / 255.0f, 178 / 255.0f, 255 / 255.0f)))).changeNoiseGeneration(builder -> builder.defaultFluid(Blocks.LAVA.defaultBlockState()))).unlockedBy(UnlockCondition.playerTookDamage((Function4<ServerLevel, ServerPlayer, DamageSource, Float, Boolean>)((Function4<ServerLevel, ServerPlayer, DamageSource, Float, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, DamageSource damageSource, Float f) -> damageSource.is(DamageTypes.LAVA)))).unlockedByWinning().requiresUnlockCount(20).register();
    public static final WorldEffect EXPLOSIVE_TRAPS = WorldEffect.builder("explosive_traps").withNameStyle(CHALLENGE_STYLE).withItemModelOf(Items.TNT).xpModifier(1.5f).incompatibleWith(WATER_WORLD, CAVE_WORLD, DARK_CAVE_WORLD).unlockedBy(UnlockCondition.mineWon((serverLevel, serverPlayer) -> CraftmineServerHooks.theGame().getAllLevels().size() > 10)).register();
    public static final WorldEffect FISH_OUT_OF_WATER = WorldEffect.builder("fish_out_of_water").withItemModelOf(Items.TROPICAL_FISH).withNameStyle(CHALLENGE_STYLE).unlockedBy(UnlockCondition.usedItem((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)((TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean>)(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemStack) -> itemStack.is(ItemTags.FISHING_ENCHANTABLE)))).incompatibleWith(DRY_LAND).xpModifier(5.0f).notRandomizable().register();
    public static final WorldEffect KUIPER_WORLD = WorldEffect.builder("kuiper_world").onMineEnter(serverLevel -> {
        // Mine level, not theGame().overworld() - same silent no-op as ETERNAL_NIGHT
        // (see CraftmineTime.setDayTime); the shared clock is resolved from the mine's
        // dimension type default clock.
        CraftmineTime.setDayTime(serverLevel, 18000L);
        CraftmineServerHooks.theGame().getGameRules().set(GameRules.ADVANCE_TIME, false, CraftmineServerHooks.theGame().server());
    }).onMineLeave(serverLevel -> CraftmineServerHooks.theGame().getGameRules().set(GameRules.ADVANCE_TIME, true, CraftmineServerHooks.theGame().server())).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.withCustomChunkGenerator((provider, biomeSource, holder, craftSeed) -> {
        HolderGetter<Biome> holderGetter = provider.lookupOrThrow(Registries.BIOME);
        FlatLevelGeneratorSettings flatLevelGeneratorSettings = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct(new Holder[0])), holderGetter.getOrThrow(Biomes.THE_VOID), List.of());
        flatLevelGeneratorSettings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
        flatLevelGeneratorSettings.updateLayers();
        return new FlatLevelSource(flatLevelGeneratorSettings);
    })).neverUnlocked().withCustomIcon("kuiper_world").register();
    public static final WorldEffect ENDER_DRAGON_BOSS_FIGHT = WorldEffect.builder("ender_dragon_boss_fight").withItemModelOf(Items.ENDER_DRAGON_SPAWN_EGG).onMineEnter(serverLevel -> ((CraftmineLevelDuck)(Object)serverLevel).startEvent(new EndDragonBattle())).modifyingWorldGen(worldGenBuilder -> worldGenBuilder.changeBiomes(biomeModificationBuilder -> biomeModificationBuilder.modifyGenerationSettings(plainBuilder -> plainBuilder.addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, worldGenBuilder.getOrThrow(EndPlacements.END_PLATFORM))))).neverUnlocked().notRandomizable().incompatibleWith(CAVE_WORLD, DARK_CAVE_WORLD).register();
    public static final WorldEffect ANGRY_GHAST_BOSS_FIGHT = WorldEffect.builder("angry_ghast_boss_fight").withItemModelOf(Items.GHAST_TEAR).onMineEnter(serverLevel -> ((CraftmineLevelDuck)(Object)serverLevel).startEvent(Battle.builder(serverLevel, "angry_ghast").withWave(builder -> builder.spawns(builder2 -> builder2.type(CraftmineContent.ANGRY_GHAST).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(25, 25, 25)))).noBossBar().countdownLabel(Component.translatable("world.event.angry_ghast")).ticksDelay(1200)).build())).neverUnlocked().notRandomizable().register();
    public static final WorldEffect RAID = WorldEffect.builder("raid").withItemModelOf(Items.OMINOUS_BOTTLE).onMineEnter(serverLevel -> ((CraftmineLevelDuck)(Object)serverLevel).startEvent(new RaidEvent())).onPlayerMineEnter(serverPlayer -> serverPlayer.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, -1))).neverUnlocked().notRandomizable().register();
    public static final WorldEffect WITHER_BOSS_FIGHT = WorldEffect.builder("wither_boss_fight").withItemModelOf(Items.WITHER_SPAWN_EGG).onMineEnter(serverLevel -> ((CraftmineLevelDuck)(Object)serverLevel).startEvent(Battle.builder(serverLevel, "wither").withWave(builder -> builder.spawns(builder2 -> builder2.type(EntityTypes.WITHER).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).noBossBar().countdownLabel(Component.translatable("world.event.wither")).ticksDelay(600)).build())).neverUnlocked().notRandomizable().register();
    public static final WorldEffect THE_ENDERMAN_BOSS_FIGHT = WorldEffect.builder("the_enderman_boss_fight").withItemModelOf(Items.ENDER_PEARL).onMineEnter(serverLevel -> ((CraftmineLevelDuck)(Object)serverLevel).startEvent(Battle.builder(serverLevel, "enderman").withWave(builder -> builder.spawns(builder2 -> builder2.type(EntityTypes.ENDERMAN).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).countdownLabel(Component.translatable("world.event.enderman")).ticksDelay(600)).build())).neverUnlocked().notRandomizable().register();
    public static final WorldEffect SPOOKY_SCARY_SKELETONS = WorldEffect.builder("spooky_scary_skeletons_boss_fight").withItemModelOf(Items.SKELETON_SKULL).onMineEnter(serverLevel -> ((CraftmineLevelDuck)(Object)serverLevel).startEvent(Battle.builder(serverLevel, "spooky_scary_skeletons").withWave(builder -> builder.spawns(builder2 -> builder2.type(EntityTypes.SKELETON).count(10).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).ticksDelay(600)).withWave(builder -> builder.spawns(builder2 -> builder2.type(EntityTypes.WITHER_SKELETON).count(10).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).ticksDelay(600)).withWave(builder -> builder.spawns(builder2 -> builder2.type(EntityTypes.WITHER_SKELETON).count(20).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).spawns(builder2 -> builder2.type(EntityTypes.SKELETON).count(20).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).ticksDelay(600)).build())).neverUnlocked().notRandomizable().register();
    public static final WorldEffect WARDEN_BOSS_FIGHT = WorldEffect.builder("warden_boss_fight").withItemModelOf(Items.WARDEN_SPAWN_EGG).onMineEnter(serverLevel -> ((CraftmineLevelDuck)(Object)serverLevel).startEvent(Battle.builder(serverLevel, "warden").withWave(builder -> builder.spawns(builder2 -> builder2.type(EntityTypes.WARDEN).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.WARDEN_ARENA))).noBossBar().countdownLabel(Component.translatable("world.event.warden"))).build())).unlockedBy(UnlockCondition.blockBreak((serverLevel, blockState) -> blockState.is(Blocks.AMETHYST_BLOCK) || blockState.is(Blocks.AMETHYST_CLUSTER) || blockState.is(Blocks.BUDDING_AMETHYST) || blockState.is(Blocks.SMALL_AMETHYST_BUD) || blockState.is(Blocks.MEDIUM_AMETHYST_BUD) || blockState.is(Blocks.LARGE_AMETHYST_BUD))).notRandomizable().register();
    public static final WorldEffect SMALL_BUT_DEADLY = WorldEffect.builder("small_but_deadly_boss_fight").withItemModelOf(Items.SILVERFISH_SPAWN_EGG).addBiomes(Biomes.WINDSWEPT_GRAVELLY_HILLS).onMineEnter(serverLevel -> ((CraftmineLevelDuck)(Object)serverLevel).startEvent(Battle.builder(serverLevel, "small_but_deadly").withWave(builder -> builder.spawns(builder2 -> builder2.type(EntityTypes.SILVERFISH).count(25).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).ticksDelay(300)).withWave(builder -> builder.spawns(builder2 -> builder2.type(EntityTypes.SILVERFISH).count(25).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).spawns(builder2 -> builder2.type(EntityTypes.ZOMBIE).useBabyMobs(true).count(3).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).ticksDelay(600)).withWave(builder -> builder.spawns(builder2 -> builder2.type(EntityTypes.SILVERFISH).count(30).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).spawns(builder2 -> builder2.type(EntityTypes.ZOMBIE).useBabyMobs(true).count(4).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).spawns(builder2 -> builder2.type(EntityTypes.VEX).count(3).withSpawnStrategy(builder3 -> builder3.type(Battle.SpawnType.NEAR_PLAYER).range(40).offset(new BlockPos(0, 3, 0)))).ticksDelay(1200)).build())).neverUnlocked().notRandomizable().register();
    // Unoriginal feature (config unoriginal_features): Mega-Spud-style elder guardian boss.
    // onMineEnter builds the prismarine arena FIRST (idempotent, marker = centre floor block),
    // then starts the ElderGuardianBattle MineEvent at the arena centre. The effect itself is
    // neverUnlocked/notRandomizable: it only ever reaches a mine through the elder_guardian_boss
    // special mine's required effects (whose unlock condition checks the config).
    public static final WorldEffect ELDER_GUARDIAN_BOSS_FIGHT = WorldEffect.builder("elder_guardian_boss_fight").withItemModelOf(Items.HEART_OF_THE_SEA).onMineEnter(serverLevel -> {
        net.minecraft.core.BlockPos arenaCenter = ElderGuardianBattle.buildArena(serverLevel);
        ((CraftmineLevelDuck)(Object)serverLevel).startEvent(new ElderGuardianBattle(serverLevel.dimension().identifier().withSuffix("/elder_guardian_boss"), arenaCenter));
    }).neverUnlocked().notRandomizable().register();

    // Unoriginal feature (config unoriginal_features): the aerial breeze boss ("The Breezy One").
    // The mine is a completely NORMAL plains mine - the only unusual thing is the 3x3 obsidian
    // platform built flush at the spawn and the NoAI breeze floating 50 blocks above it. Boss
    // bar = remaining height; smashing him with the mace (MACE_SMASH) pushes him down; a full
    // 3x3 obsidian layer under him after a descent detonates him (the intended cheese).
    public static final WorldEffect BREEZE_BOSS_FIGHT = WorldEffect.builder("breeze_boss_fight").withItemModelOf(Items.WIND_CHARGE).onMineEnter(serverLevel -> {
        net.minecraft.core.BlockPos platform = BreezeBossBattle.buildPlatform(serverLevel);
        ((CraftmineLevelDuck)(Object)serverLevel).startEvent(new BreezeBossBattle(serverLevel.dimension().identifier().withSuffix("/breeze_boss"), platform));
    }).neverUnlocked().notRandomizable().register();

    public static boolean isDarkPlace(ServerLevel serverLevel, BlockPos blockPos) {
        for (BlockPos blockPos2 : BlockPos.betweenClosed(blockPos.offset(-1, -1, -1), blockPos.offset(1, 1, 1))) {
            if (serverLevel.getMaxLocalRawBrightness(blockPos2) <= 0) continue;
            return false;
        }
        return true;
    }

    public static WorldEffect bootstrap(Registry<WorldEffect> registry) {
        return ONE_HP;
    }

    public static WorldEffectSet sets(Registry<WorldEffectSet> registry) {
        return WORLD_TYPE;
    }

    public static WorldEffectSet mustHaveAtLeastOne(String string) {
        return Registry.register(CraftmineRegistries.WORLD_EFFECT_SET, string, new WorldEffectSet(false));
    }

    public static WorldEffectSet mustHaveOnlyOne(String string) {
        return Registry.register(CraftmineRegistries.WORLD_EFFECT_SET, string, new WorldEffectSet(true));
    }

    public static ItemStack createEffectItem(WorldEffect worldEffect, boolean bl) {
        return WorldEffects.createEffectItem(Component.translatable("item.minecraft.mine_ingredient.desc", worldEffect.name()), bl, List.of(worldEffect));
    }

    public static ItemStack createEffectItem(Component component, boolean bl, List<WorldEffect> list) {
        ItemStack itemStack = CraftmineContent.MINE_INGREDIENT.getDefaultInstance();
        itemStack.set(CraftmineDataComponents.WORLD_MODIFIERS, new WorldModifiers(list, list.size() <= 1));
        itemStack.set(DataComponents.ITEM_NAME, component);
        if (bl) {
            itemStack.set(CraftmineDataComponents.WORLD_EFFECT_UNLOCK, Unit.INSTANCE);
        }
        return itemStack;
    }

    public static <T extends WorldEffectComponent> Stream<T> componentsOfType(List<WorldEffect> list, Class<T> clazz) {
        return list.stream().flatMap(worldEffect -> worldEffect.componentsOfType(clazz));
    }

    /** Port of craftmine's DimensionType.madeUltrawarm (26.3: ultrawarm lives in environment attributes). */
    static DimensionType craftmine$madeUltrawarm(DimensionType dt) {
        EnvironmentAttributeMap attrs = EnvironmentAttributeMap.builder()
            .putAll(dt.attributes())
            .set(EnvironmentAttributes.WATER_EVAPORATES, true)
            .set(EnvironmentAttributes.FAST_LAVA, true)
            .build();
        return new DimensionType(dt.hasFixedTime(), dt.hasSkyLight(), dt.hasCeiling(), dt.hasEnderDragonFight(), dt.coordinateScale(), dt.minY(), dt.height(), dt.logicalHeight(), dt.infiniburn(), dt.ambientLight(), dt.monsterSettings(), dt.skybox(), dt.cardinalLightType(), attrs, dt.timelines(), dt.defaultClock());
    }

}

