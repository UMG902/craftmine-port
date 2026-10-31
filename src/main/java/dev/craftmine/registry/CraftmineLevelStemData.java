package dev.craftmine.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import dev.craftmine.level.biome.CraftmineExitType;
import net.minecraft.world.level.dimension.LevelStem;
import dev.craftmine.mines.MineSpawnStrategy;
import dev.craftmine.mines.SpecialMine;
import dev.craftmine.mines.WorldEffect;

/**
 * Craftmine's forked LevelStem carried per-dimension mine metadata
 * (effects / special mine / spawn strategy). 26.3's LevelStem is a plain
 * (DimensionType, ChunkGenerator) record, so the metadata lives here instead,
 * keyed by the LevelStem registry key of the generated dimension.
 * Populated by DimensionGenerator.generateDimension.
 */
public final class CraftmineLevelStemData {
    private CraftmineLevelStemData() {
    }

    public record Entry(List<WorldEffect> effects, Optional<SpecialMine> mine, MineSpawnStrategy spawn, long craftSeed, Map<Identifier, CraftmineExitType> exitTypes) {
        public static final Entry DEFAULT = new Entry(List.of(), Optional.empty(), MineSpawnStrategy.SURFACE, 0L, Map.of());
    }

    private static final Map<ResourceKey<LevelStem>, Entry> DATA = new ConcurrentHashMap<ResourceKey<LevelStem>, Entry>();

    public static void register(ResourceKey<LevelStem> stemKey, List<WorldEffect> effects, Optional<SpecialMine> mine, MineSpawnStrategy spawn) {
        register(stemKey, effects, mine, spawn, 0L);
    }

    public static void register(ResourceKey<LevelStem> stemKey, List<WorldEffect> effects, Optional<SpecialMine> mine, MineSpawnStrategy spawn, long craftSeed) {
        register(stemKey, effects, mine, spawn, craftSeed, Map.of());
    }

    public static void register(ResourceKey<LevelStem> stemKey, List<WorldEffect> effects, Optional<SpecialMine> mine, MineSpawnStrategy spawn, long craftSeed, Map<Identifier, CraftmineExitType> exitTypes) {
        DATA.put(stemKey, new Entry(List.copyOf(effects), mine, spawn, craftSeed, Map.copyOf(exitTypes)));
    }

    public static Entry get(ResourceKey<LevelStem> stemKey) {
        return DATA.getOrDefault(stemKey, Entry.DEFAULT);
    }

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /**
     * Task 58 (first-entry mine fixes, boot side): rebuilds this map from the
     * generated mine datapacks on disk. The live entry path
     * (DimensionGenerator.generateDimension) registers craftSeed / effects /
     * mine / spawn / exitTypes here, but the map is JVM-static and therefore
     * EMPTY after a server restart - so every rejoined mine regenerated with
     * the RAW world seed (the ChunkMapMixin craftSeed XOR read 0 => "different
     * seed" terrain, exactly the reported first-join vs rejoin mismatch), lost
     * its active ingredient effects, fell back to the SURFACE spawn strategy
     * and had no exit types. The original snapshot got all of this back for
     * free from its WorldReloader datapack reload. Only the metadata fields
     * are parsed; the dimension/type/generator fields are left to the vanilla
     * datapack-dimension boot path that already creates these levels.
     *
     * Called from the MinecraftServer constructor tail (after worldStem is
     * loaded and the custom registries are bootstrapped, before vanilla
     * createLevels -> new ServerLevel -> ChunkMap reads craftSeed).
     */
    public static void restoreFromWorldDir(java.nio.file.Path datapacksDir, net.minecraft.core.RegistryAccess registryAccess) {
        if (datapacksDir == null || !java.nio.file.Files.isDirectory(datapacksDir)) {
            return;
        }
        // Task 59: heal stale generated pack.mcmeta formats on disk before anything
        // else reads them (this ctor-tail runs after boot pack discovery, so the
        // repair benefits the NEXT discovery; the boot selection itself is healed
        // by PackStuff's disabled-list re-enable).
        dev.craftmine.server.CraftminePackRepair.repairWorldDatapackFormats(datapacksDir);
        int restored = 0;
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(datapacksDir)) {
            for (java.nio.file.Path jsonPath : (Iterable<java.nio.file.Path>) stream::iterator) {
                String fileName = jsonPath.getFileName() == null ? "" : jsonPath.getFileName().toString();
                if (!fileName.endsWith(".json")) {
                    continue;
                }
                java.nio.file.Path dimensionDir = jsonPath.getParent();
                if (dimensionDir == null || !"dimension".equals(dimensionDir.getFileName().toString())) {
                    continue;
                }
                java.nio.file.Path namespaceDir = dimensionDir.getParent();
                if (namespaceDir == null || namespaceDir.getParent() == null
                    || !"data".equals(namespaceDir.getParent().getFileName().toString())) {
                    continue;
                }
                try {
                    com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(jsonPath));
                    if (!root.isJsonObject() || !root.getAsJsonObject().has("spawn")) {
                        // generated mine stems always carry a spawn strategy;
                        // anything else is not ours
                        continue;
                    }
                    com.google.gson.JsonObject object = root.getAsJsonObject();
                    Identifier stemId = Identifier.fromNamespaceAndPath(
                        namespaceDir.getFileName().toString(), fileName.substring(0, fileName.length() - 5));
                    long craftSeed = object.has("craft_seed") && object.get("craft_seed").isJsonPrimitive()
                        ? object.get("craft_seed").getAsLong()
                        : 0L;
                    MineSpawnStrategy spawn = MineSpawnStrategy.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, object.get("spawn"))
                        .result().orElse(MineSpawnStrategy.SURFACE);
                    List<WorldEffect> effects = new java.util.ArrayList<WorldEffect>();
                    if (object.has("effects") && object.get("effects").isJsonArray()) {
                        for (com.google.gson.JsonElement element : object.getAsJsonArray("effects")) {
                            WorldEffect.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, element)
                                .result().ifPresent(effects::add);
                        }
                        if (effects.size() != object.getAsJsonArray("effects").size()) {
                            LOGGER.warn("craftmine-stem-restore: {} - only {} of {} effects parsed (registry not ready?)",
                                stemId, effects.size(), object.getAsJsonArray("effects").size());
                        }
                    }
                    java.util.Optional<SpecialMine> mine = java.util.Optional.empty();
                    if (object.has("mine")) {
                        mine = SpecialMine.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, object.get("mine")).result();
                    }
                    java.util.Map<Identifier, CraftmineExitType> exitTypes = new java.util.HashMap<Identifier, CraftmineExitType>();
                    if (object.has("exit_types") && object.get("exit_types").isJsonObject()) {
                        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : object.getAsJsonObject("exit_types").entrySet()) {
                            CraftmineExitType.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, entry.getValue())
                                .result().ifPresent(exitType -> exitTypes.put(Identifier.parse(entry.getKey()), exitType));
                        }
                    }
                    ResourceKey<LevelStem> stemKey = ResourceKey.create(net.minecraft.core.registries.Registries.LEVEL_STEM, stemId);
                    register(stemKey, effects, mine, spawn, craftSeed, java.util.Collections.unmodifiableMap(exitTypes));
                    restored++;
                }
                catch (Exception exception) {
                    LOGGER.error("craftmine-stem-restore: failed to restore {}", jsonPath, exception);
                }
            }
        }
        catch (Exception exception) {
            LOGGER.error("craftmine-stem-restore: failed to scan {}", datapacksDir, exception);
        }
        if (restored > 0) {
            LOGGER.info("craftmine-stem-restore: restored {} generated mine stem(s) from disk", restored);
        }
    }
}
