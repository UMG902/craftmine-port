package dev.craftmine.server;

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import dev.craftmine.server.TheGame;
import dev.craftmine.server.GeneratedMarkerMetadataSection;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.biome.Biome;
import dev.craftmine.level.biome.CraftmineExitType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import dev.craftmine.mines.MineSpawnStrategy;
import dev.craftmine.mines.SpecialMine;
import dev.craftmine.mines.WorldEffect;
import dev.craftmine.mines.WorldEffects;
import dev.craftmine.mines.WorldGenBuilder;
import dev.craftmine.mines.WorldGenEffect;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.slf4j.Logger;
import dev.craftmine.registry.CraftmineContent;

public class DimensionGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String LEVEL_PREFIX = "level";

    public static GeneratedDimension generateDimension(TheGame theGame, List<WorldEffect> list, Optional<SpecialMine> optional) {
        ResourceKey<DimensionType> resourceKey;
        int n = ((dev.craftmine.duck.CraftmineLevelDataDuck) (Object) theGame.getWorldData()).incrementAndGetLevelCount();
        Identifier resourceLocation = Identifier.withDefaultNamespace(LEVEL_PREFIX + n);
        Path path = theGame.server().getWorldPath(LevelResource.DATAPACK_DIR).resolve(resourceLocation.getPath());
        MutableComponent mutableComponent = Component.translatable("minecraftlike.pack_name", n);
        RegistryOps<JsonElement> registryOps = theGame.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        ArrayList<CompletableFuture<?>> arrayList = new ArrayList<CompletableFuture<?>>();
        arrayList.add(DimensionGenerator.save(new DimensionPackMetadata(new PackMetadataSection(mutableComponent, new net.minecraft.util.InclusiveRange<net.minecraft.server.packs.metadata.pack.PackFormat>(net.minecraft.SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.SERVER_DATA))), new GeneratedMarkerMetadataSection()), DimensionPackMetadata.CODEC, registryOps, path.resolve("pack.mcmeta")));
        Path path2 = path.resolve("data").resolve(resourceLocation.getNamespace());
        Holder.Reference<DimensionType> reference = theGame.registryAccess().getOrThrow(CraftmineContent.GENERATED_DIMENSION_TYPE);
        WorldGenBuilder worldGenBuilder = new WorldGenBuilder(theGame.registryAccess());
        WorldEffects.componentsOfType(list, WorldGenEffect.class).forEach(worldGenEffect -> worldGenEffect.modifyWorld(worldGenBuilder));
        Optional<DimensionType> optional2 = worldGenBuilder.createDimensionType(reference.value());
        if (optional2.isEmpty()) {
            resourceKey = reference.unwrapKey().get();
        } else {
            DimensionType dimensionType = optional2.get();
            Path dimensionTypePath = path2.resolve("dimension_type").resolve(resourceLocation.getPath() + ".json");
            arrayList.add(DimensionGenerator.save(dimensionType, DimensionType.DIRECT_CODEC, registryOps, dimensionTypePath));
            resourceKey = ResourceKey.create(Registries.DIMENSION_TYPE, resourceLocation);
        }
        List<WorldGenBuilder.ModifiedBiome> modifiedBiomes = worldGenBuilder.createModifiedBiomes(theGame.registryAccess().lookupOrThrow(Registries.BIOME), resourceLocation.getPath());
        // Materialize the mine's entries into the WORLD registry layer BEFORE the
        // chunk generator is built and the datapack JSONs are written: the original
        // ran this code post-WorldReloader, where the biomes/dimension types were
        // already registered. Registering them now lets the chunk generator resolve
        // registry references (not inlined direct holders), keeps the saved JSONs
        // key-referenced, and marks connected clients stale so the entry path can
        // push them through a configuration-phase registry resync.
        RegistryAccess.Frozen materializedAccess = dev.craftmine.server.CraftmineRegistryMaterialization.materialize(
            theGame,
            modifiedBiomes,
            optional2.map(dimensionType -> Map.entry(resourceKey, dimensionType)),
            resourceLocation.getPath()
        );
        worldGenBuilder.setRegistries(materializedAccess);
        registryOps = materializedAccess.createSerializationContext(JsonOps.INSTANCE);
        for (WorldGenBuilder.ModifiedBiome modifiedBiome : modifiedBiomes) {
            arrayList.add(DimensionGenerator.save(modifiedBiome.biome(), Biome.DIRECT_CODEC, registryOps, path2.resolve("worldgen").resolve("biome").resolve(modifiedBiome.modified().identifier().getPath() + ".json")));
        }
        // Task 35 (BugMine "Apply Level Biome Tags", always-on): vanilla biome
        // tags must reference the mine's modified biomes, otherwise structures
        // can never spawn in crafted mines and spawn-variant / behavior tags
        // (frogs, foxes, Dry Land, sieges...) are lost.
        DimensionGenerator.writeBiomeTags(path2, resourceLocation.getPath(), modifiedBiomes);
        Path dimensionPath = path2.resolve("dimension").resolve(resourceLocation.getPath() + ".json");
        ChunkGenerator chunkGenerator = worldGenBuilder.createChunkGenerator(resourceLocation.getPath());
        // DIFF-01 (fork fce.a 1:1): the per-craft seed the fork stored on the mine's
        // noise settings - its RandomState XORed worldSeed ^ seed so identically
        // configured mines still rolled different terrain. 26.3's NoiseGeneratorSettings
        // has no seed field, so the value rides CraftmineLevelStemData and ChunkMapMixin
        // applies the XOR when the level's RandomState is created. Also persisted in the
        // stem JSON next to effects/mine/spawn (boot-side reload of that metadata is a
        // known separate gap, shared with those fields).
        long craftSeed = worldGenBuilder.craftSeed();
        // Task 42 (mine exits): persist the mine's biome exit types next to the
        // other stem metadata so a future boot-side reload can restore them; the
        // session-side static map (CraftmineBiomeExitTypes) already carries the
        // modified-id entries applied inside createModifiedBiomes.
        java.util.Map<net.minecraft.resources.Identifier, CraftmineExitType> exitTypes = new java.util.HashMap<net.minecraft.resources.Identifier, CraftmineExitType>();
        for (WorldGenBuilder.ModifiedBiome modifiedBiome : modifiedBiomes) {
            CraftmineExitType exitType = dev.craftmine.level.biome.CraftmineBiomeExitTypes.get(modifiedBiome.modified().identifier());
            if (exitType != CraftmineExitType.NONE) {
                exitTypes.put(modifiedBiome.modified().identifier(), exitType);
            }
        }
        arrayList.add(DimensionGenerator.save(new FakeLevelStem(resourceKey, Optional.of(chunkGenerator), list, optional, worldGenBuilder.spawnStrategy(), craftSeed, exitTypes), FakeLevelStem.CODEC, registryOps, dimensionPath));
        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, resourceLocation);
        dev.craftmine.registry.CraftmineLevelStemData.register(stemKey, list, optional, worldGenBuilder.spawnStrategy(), craftSeed, java.util.Map.copyOf(exitTypes));
        return new GeneratedDimension(stemKey, () -> CompletableFuture.allOf(arrayList.toArray(new CompletableFuture[0])).join());
    }

    // Task 35 (BugMine "Apply Level Biome Tags", always-on): the snapshot's
    // generated mine datapacks carry no biome tags, so structures could never
    // spawn in crafted mines and spawn-variant / behavior tags (frogs, foxes,
    // Dry Land ingredient, sieges...) were lost.
    // Task 37: regenerated by scripts/gen_biome_tags_table_263.py as the UNION
    // of vanilla 26.3 and fork (25w14craftmine) memberships - adds the 26.3
    // abandoned_camp_* (campsite) structure tags, zombie nautilus variant tag
    // and the dappled_forest/sulfur_caves biomes, drops three tags no 26.3
    // class references anymore (has_closer_water_fog, plays_underwater_music,
    // without_patrol_spawns); keeps the fork's nether/end entries in
    // is_overworld/stronghold/trial_chambers (mines span the full palette).
    public static final String[][] BIOME_TAGS = {
        {"is_badlands", "badlands eroded_badlands wooded_badlands"},
        {"is_beach", "beach snowy_beach"},
        {"is_deep_ocean", "deep_cold_ocean deep_frozen_ocean deep_lukewarm_ocean deep_ocean"},
        {"is_end", "end_barrens end_highlands end_midlands small_end_islands the_end"},
        {"is_forest", "birch_forest dappled_forest dark_forest flower_forest forest grove old_growth_birch_forest pale_garden"},
        {"is_hill", "windswept_forest windswept_gravelly_hills windswept_hills"},
        {"is_jungle", "bamboo_jungle jungle sparse_jungle"},
        {"is_mountain", "cherry_grove frozen_peaks jagged_peaks meadow snowy_slopes stony_peaks"},
        {"is_nether", "basalt_deltas crimson_forest nether_wastes soul_sand_valley warped_forest"},
        {"is_ocean", "cold_ocean deep_cold_ocean deep_frozen_ocean deep_lukewarm_ocean deep_ocean frozen_ocean lukewarm_ocean ocean warm_ocean"},
        {"is_overworld", "badlands bamboo_jungle basalt_deltas beach birch_forest cherry_grove cold_ocean crimson_forest dappled_forest dark_forest deep_cold_ocean deep_dark deep_frozen_ocean deep_lukewarm_ocean deep_ocean desert dripstone_caves end_barrens end_highlands end_midlands eroded_badlands flower_forest forest frozen_ocean frozen_peaks frozen_river grove ice_spikes jagged_peaks jungle lukewarm_ocean lush_caves mangrove_swamp meadow mushroom_fields nether_wastes ocean old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga pale_garden plains river savanna savanna_plateau small_end_islands snowy_beach snowy_plains snowy_slopes snowy_taiga soul_sand_valley sparse_jungle stony_peaks stony_shore sulfur_caves sunflower_plains swamp taiga the_end warm_ocean warped_forest windswept_forest windswept_gravelly_hills windswept_hills windswept_savanna wooded_badlands"},
        {"is_river", "frozen_river river"},
        {"is_savanna", "savanna savanna_plateau windswept_savanna"},
        {"is_taiga", "old_growth_pine_taiga old_growth_spruce_taiga snowy_taiga taiga"},
        {"has_structure/abandoned_camp_bamboo_jungle", "bamboo_jungle"},
        {"has_structure/abandoned_camp_birch_forest", "birch_forest"},
        {"has_structure/abandoned_camp_cherry_grove", "cherry_grove"},
        {"has_structure/abandoned_camp_dappled_forest", "dappled_forest"},
        {"has_structure/abandoned_camp_flower_forest", "flower_forest"},
        {"has_structure/abandoned_camp_forest", "forest"},
        {"has_structure/abandoned_camp_meadow", "meadow"},
        {"has_structure/abandoned_camp_old_growth_birch_forest", "old_growth_birch_forest"},
        {"has_structure/abandoned_camp_old_growth_pine_taiga", "old_growth_pine_taiga"},
        {"has_structure/abandoned_camp_old_growth_spruce_taiga", "old_growth_spruce_taiga"},
        {"has_structure/abandoned_camp_pale_garden", "pale_garden"},
        {"has_structure/abandoned_camp_savanna", "savanna"},
        {"has_structure/abandoned_camp_snowy_taiga", "snowy_taiga"},
        {"has_structure/abandoned_camp_sparse_jungle", "sparse_jungle"},
        {"has_structure/abandoned_camp_swamp", "swamp"},
        {"has_structure/abandoned_camp_taiga", "taiga"},
        {"has_structure/abandoned_camp_windswept_forest", "windswept_forest"},
        {"has_structure/abandoned_camp_wooded_badlands", "wooded_badlands"},
        {"has_structure/ancient_city", "deep_dark"},
        {"has_structure/bastion_remnant", "crimson_forest nether_wastes soul_sand_valley warped_forest"},
        {"has_structure/buried_treasure", "beach snowy_beach"},
        {"has_structure/desert_pyramid", "desert"},
        {"has_structure/end_city", "end_highlands end_midlands"},
        {"has_structure/igloo", "snowy_plains snowy_slopes snowy_taiga"},
        {"has_structure/jungle_temple", "bamboo_jungle jungle"},
        {"has_structure/mineshaft", "bamboo_jungle beach birch_forest cherry_grove cold_ocean dappled_forest dark_forest deep_cold_ocean deep_frozen_ocean deep_lukewarm_ocean deep_ocean desert dripstone_caves flower_forest forest frozen_ocean frozen_peaks frozen_river grove ice_spikes jagged_peaks jungle lukewarm_ocean lush_caves mangrove_swamp meadow mushroom_fields ocean old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga pale_garden plains river savanna savanna_plateau snowy_beach snowy_plains snowy_slopes snowy_taiga sparse_jungle stony_peaks stony_shore sulfur_caves sunflower_plains swamp taiga warm_ocean windswept_forest windswept_gravelly_hills windswept_hills windswept_savanna"},
        {"has_structure/mineshaft_mesa", "badlands eroded_badlands wooded_badlands"},
        {"has_structure/nether_fortress", "basalt_deltas crimson_forest nether_wastes soul_sand_valley warped_forest"},
        {"has_structure/nether_fossil", "soul_sand_valley"},
        {"has_structure/ocean_monument", "deep_cold_ocean deep_frozen_ocean deep_lukewarm_ocean deep_ocean"},
        {"has_structure/ocean_ruin_cold", "cold_ocean deep_cold_ocean deep_frozen_ocean deep_ocean frozen_ocean ocean"},
        {"has_structure/ocean_ruin_warm", "deep_lukewarm_ocean lukewarm_ocean warm_ocean"},
        {"has_structure/pillager_outpost", "cherry_grove desert frozen_peaks grove jagged_peaks meadow plains savanna snowy_plains snowy_slopes stony_peaks taiga"},
        {"has_structure/ruined_portal_desert", "desert"},
        {"has_structure/ruined_portal_jungle", "bamboo_jungle jungle sparse_jungle"},
        {"has_structure/ruined_portal_mountain", "badlands cherry_grove eroded_badlands frozen_peaks jagged_peaks meadow savanna_plateau snowy_slopes stony_peaks stony_shore windswept_forest windswept_gravelly_hills windswept_hills windswept_savanna wooded_badlands"},
        {"has_structure/ruined_portal_nether", "basalt_deltas crimson_forest nether_wastes soul_sand_valley warped_forest"},
        {"has_structure/ruined_portal_ocean", "cold_ocean deep_cold_ocean deep_frozen_ocean deep_lukewarm_ocean deep_ocean frozen_ocean lukewarm_ocean ocean warm_ocean"},
        {"has_structure/ruined_portal_standard", "beach birch_forest dappled_forest dark_forest dripstone_caves flower_forest forest frozen_river grove ice_spikes lush_caves mushroom_fields old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga pale_garden plains river savanna snowy_beach snowy_plains snowy_taiga sulfur_caves sunflower_plains taiga"},
        {"has_structure/ruined_portal_swamp", "mangrove_swamp swamp"},
        {"has_structure/shipwreck", "cold_ocean deep_cold_ocean deep_frozen_ocean deep_lukewarm_ocean deep_ocean frozen_ocean lukewarm_ocean ocean warm_ocean"},
        {"has_structure/shipwreck_beached", "beach snowy_beach"},
        {"has_structure/stronghold", "badlands bamboo_jungle basalt_deltas beach birch_forest cherry_grove cold_ocean crimson_forest dappled_forest dark_forest deep_cold_ocean deep_dark deep_frozen_ocean deep_lukewarm_ocean deep_ocean desert dripstone_caves end_barrens end_highlands end_midlands eroded_badlands flower_forest forest frozen_ocean frozen_peaks frozen_river grove ice_spikes jagged_peaks jungle lukewarm_ocean lush_caves mangrove_swamp meadow mushroom_fields nether_wastes ocean old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga pale_garden plains river savanna savanna_plateau small_end_islands snowy_beach snowy_plains snowy_slopes snowy_taiga soul_sand_valley sparse_jungle stony_peaks stony_shore sulfur_caves sunflower_plains swamp taiga the_end warm_ocean warped_forest windswept_forest windswept_gravelly_hills windswept_hills windswept_savanna wooded_badlands"},
        {"has_structure/swamp_hut", "swamp"},
        {"has_structure/trail_ruins", "jungle old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga snowy_taiga taiga"},
        {"has_structure/trial_chambers", "badlands bamboo_jungle basalt_deltas beach birch_forest cherry_grove cold_ocean crimson_forest dappled_forest dark_forest deep_cold_ocean deep_frozen_ocean deep_lukewarm_ocean deep_ocean desert dripstone_caves end_barrens end_highlands end_midlands eroded_badlands flower_forest forest frozen_ocean frozen_peaks frozen_river grove ice_spikes jagged_peaks jungle lukewarm_ocean lush_caves mangrove_swamp meadow mushroom_fields nether_wastes ocean old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga pale_garden plains river savanna savanna_plateau small_end_islands snowy_beach snowy_plains snowy_slopes snowy_taiga soul_sand_valley sparse_jungle stony_peaks stony_shore sulfur_caves sunflower_plains swamp taiga the_end warm_ocean warped_forest windswept_forest windswept_gravelly_hills windswept_hills windswept_savanna wooded_badlands"},
        {"has_structure/village_desert", "desert"},
        {"has_structure/village_plains", "meadow plains"},
        {"has_structure/village_savanna", "savanna"},
        {"has_structure/village_snowy", "snowy_plains"},
        {"has_structure/village_taiga", "taiga"},
        {"has_structure/woodland_mansion", "dark_forest pale_garden"},
        {"allows_surface_slime_spawns", "mangrove_swamp swamp"},
        {"allows_tropical_fish_spawns_at_any_height", "lush_caves"},
        {"increased_fire_burnout", "bamboo_jungle frozen_peaks jagged_peaks jungle mangrove_swamp mushroom_fields snowy_slopes swamp"},
        {"mineshaft_blocking", "deep_dark"},
        {"more_frequent_drowned_spawns", "frozen_river river"},
        {"polar_bears_spawn_on_alternate_blocks", "deep_frozen_ocean frozen_ocean"},
        {"produces_corals_from_bonemeal", "warm_ocean"},
        {"reduce_water_ambient_spawns", "frozen_river river"},
        {"required_ocean_monument_surrounding", "cold_ocean deep_cold_ocean deep_frozen_ocean deep_lukewarm_ocean deep_ocean frozen_ocean frozen_river lukewarm_ocean ocean river warm_ocean"},
        {"snow_golem_melts", "badlands basalt_deltas crimson_forest desert eroded_badlands nether_wastes savanna savanna_plateau soul_sand_valley warped_forest windswept_savanna wooded_badlands"},
        {"spawns_cold_variant_farm_animals", "cold_ocean dappled_forest deep_cold_ocean deep_dark deep_frozen_ocean end_barrens end_highlands end_midlands frozen_ocean frozen_peaks frozen_river grove ice_spikes jagged_peaks old_growth_pine_taiga old_growth_spruce_taiga small_end_islands snowy_beach snowy_plains snowy_slopes snowy_taiga stony_peaks taiga the_end windswept_forest windswept_gravelly_hills windswept_hills"},
        {"spawns_cold_variant_frogs", "deep_dark deep_frozen_ocean end_barrens end_highlands end_midlands frozen_ocean frozen_peaks frozen_river grove ice_spikes jagged_peaks small_end_islands snowy_beach snowy_plains snowy_slopes snowy_taiga the_end"},
        {"spawns_coral_variant_zombie_nautilus", "warm_ocean"},
        {"spawns_gold_rabbits", "desert"},
        {"spawns_snow_foxes", "frozen_ocean frozen_peaks frozen_river grove ice_spikes jagged_peaks snowy_beach snowy_plains snowy_slopes snowy_taiga"},
        {"spawns_warm_variant_farm_animals", "badlands bamboo_jungle basalt_deltas crimson_forest deep_lukewarm_ocean desert eroded_badlands jungle lukewarm_ocean mangrove_swamp nether_wastes savanna savanna_plateau soul_sand_valley sparse_jungle warm_ocean warped_forest windswept_savanna wooded_badlands"},
        {"spawns_warm_variant_frogs", "badlands bamboo_jungle basalt_deltas crimson_forest desert eroded_badlands jungle mangrove_swamp nether_wastes savanna savanna_plateau soul_sand_valley sparse_jungle warm_ocean warped_forest windswept_savanna wooded_badlands"},
        {"spawns_white_rabbits", "frozen_ocean frozen_peaks frozen_river grove ice_spikes jagged_peaks snowy_beach snowy_plains snowy_slopes snowy_taiga"},
        {"stronghold_biased_to", "badlands bamboo_jungle birch_forest cherry_grove dark_forest desert dripstone_caves eroded_badlands flower_forest forest frozen_peaks grove ice_spikes jagged_peaks jungle lush_caves meadow mushroom_fields old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga pale_garden plains savanna savanna_plateau snowy_plains snowy_slopes snowy_taiga sparse_jungle stony_peaks sulfur_caves sunflower_plains taiga windswept_forest windswept_gravelly_hills windswept_hills windswept_savanna wooded_badlands"},
        {"water_on_map_outlines", "cold_ocean deep_cold_ocean deep_frozen_ocean deep_lukewarm_ocean deep_ocean frozen_ocean frozen_river lukewarm_ocean mangrove_swamp ocean river swamp warm_ocean"},
        {"without_wandering_trader_spawns", "hub the_void"},
        {"without_zombie_sieges", "mushroom_fields"},
    };

    private static void writeBiomeTags(java.nio.file.Path dataDir, String levelPath, List<WorldGenBuilder.ModifiedBiome> modifiedBiomes) {
        java.util.Set<Identifier> modifiedIdentifiers = new java.util.HashSet<Identifier>();
        for (WorldGenBuilder.ModifiedBiome modifiedBiome : modifiedBiomes) {
            modifiedIdentifiers.add(modifiedBiome.modified().identifier());
        }
        for (String[] tag : BIOME_TAGS) {
            ArrayList<String> values = new ArrayList<String>();
            for (String biome : tag[1].split(" ")) {
                Identifier id = Identifier.withDefaultNamespace(levelPath + "/" + biome);
                if (modifiedIdentifiers.contains(id)) {
                    values.add(id.toString());
                }
            }
            if (values.isEmpty()) {
                continue;
            }
            java.nio.file.Path tagPath = dataDir.resolve("tags").resolve("worldgen").resolve("biome").resolve(tag[0] + ".json");
            StringBuilder stringBuilder = new StringBuilder("{\n  \"values\": [");
            for (int i = 0; i < values.size(); ++i) {
                if (i > 0) {
                    stringBuilder.append(',');
                }
                stringBuilder.append("\n    \"").append(values.get(i)).append('"');
            }
            stringBuilder.append("\n  ]\n}");
            try {
                FileUtil.createDirectoriesSafe(tagPath.getParent());
                Files.writeString(tagPath, stringBuilder.toString(), StandardCharsets.UTF_8);
            }
            catch (IOException iOException) {
                LOGGER.error("Failed to write biome tag {}", (Object)tagPath, (Object)iOException);
            }
        }
    }

        private static <T> CompletableFuture<?> save(T t, Codec<T> codec, RegistryOps<JsonElement> registryOps, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                DataResult dataResult = codec.encodeStart((DynamicOps)registryOps, t);
                if (dataResult.isError()) {
                    LOGGER.error("Failed to encode entry {}: {}", (Object)path, (Object)dataResult.error());
                    return;
                }
                try (JsonWriter jsonWriter = new JsonWriter((Writer)new OutputStreamWriter((OutputStream)byteArrayOutputStream, StandardCharsets.UTF_8));){
                    jsonWriter.setSerializeNulls(false);
                    jsonWriter.setIndent("  ");
                    GsonHelper.writeValue(jsonWriter, (JsonElement)dataResult.getOrThrow(), DataProvider.KEY_COMPARATOR);
                }
                FileUtil.createDirectoriesSafe(path.getParent());
                Files.write(path, byteArrayOutputStream.toByteArray(), new OpenOption[0]);
            }
            catch (IOException iOException) {
                LOGGER.error("Failed to save file to {}", (Object)path, (Object)iOException);
            }
        }, Util.backgroundExecutor().forName("saveDimension"));
    }

    record DimensionPackMetadata(PackMetadataSection pack, GeneratedMarkerMetadataSection generated) {
        public static final Codec<DimensionPackMetadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(net.minecraft.server.packs.metadata.pack.PackMetadataSection.SERVER_TYPE.codec().fieldOf("pack").forGetter(DimensionPackMetadata::pack), GeneratedMarkerMetadataSection.CODEC.fieldOf("generated").forGetter(DimensionPackMetadata::generated)).apply(instance, DimensionPackMetadata::new));
    }

    record FakeLevelStem(ResourceKey<DimensionType> type, Optional<ChunkGenerator> generator, List<WorldEffect> effects, Optional<SpecialMine> mine, MineSpawnStrategy spawn, long craftSeed, Map<net.minecraft.resources.Identifier, CraftmineExitType> exitTypes) {
        public static final Codec<FakeLevelStem> CODEC = RecordCodecBuilder.create(instance -> instance.group(ResourceKey.codec(Registries.DIMENSION_TYPE).fieldOf("type").forGetter(FakeLevelStem::type), ChunkGenerator.CODEC.optionalFieldOf("generator").forGetter(FakeLevelStem::generator), WorldEffect.CODEC.listOf().fieldOf("effects").forGetter(FakeLevelStem::effects), SpecialMine.CODEC.optionalFieldOf("mine").forGetter(FakeLevelStem::mine), MineSpawnStrategy.CODEC.fieldOf("spawn").forGetter(FakeLevelStem::spawn), Codec.LONG.optionalFieldOf("craft_seed", 0L).forGetter(FakeLevelStem::craftSeed), Codec.unboundedMap(net.minecraft.resources.Identifier.CODEC, CraftmineExitType.CODEC).optionalFieldOf("exit_types", Map.of()).forGetter(FakeLevelStem::exitTypes)).apply(instance, FakeLevelStem::new));
    }

    public record GeneratedDimension(ResourceKey<LevelStem> id, Runnable synchronize) {
    }
}

