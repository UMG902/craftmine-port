package dev.craftmine.mines;

import dev.craftmine.worldgen.NoiseGeneratorSettingsBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import dev.craftmine.level.biome.BiomeBuilder;
import dev.craftmine.level.biome.BiomeModificationBuilder;
import net.minecraft.world.level.biome.BiomeSource;
import dev.craftmine.level.biome.CraftmineBiomeExitTypes;
import dev.craftmine.level.biome.CraftmineExitType;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import dev.craftmine.worldgen.MaterialRuleBiomeMapper;
import org.apache.commons.lang3.function.TriFunction;

/**
 * Assembles a crafted mine's world generation on top of vanilla pieces: the enabled
 * biome set, mob spawns, biome modifications, noise settings overrides, the spawn
 * strategy and (rarely) a fully custom chunk generator. One builder instance exists
 * per mine and lives from craft time (effects contribute through
 * {@code WorldGenEffect}) until the dimension is created ({@code DimensionGenerator}).
 *
 * <p>{@link #craftSeed()} reproduces the fork's per-mine seeding trick so every
 * crafted mine rolls its own terrain.</p>
 */
public class WorldGenBuilder {
    private final Set<ResourceKey<Biome>> enabledBiomes = new HashSet<ResourceKey<Biome>>();
    private final Map<MobCategory, List<Weighted<MobSpawnSettings.SpawnerData>>> enabledMobs = new HashMap<MobCategory, List<Weighted<MobSpawnSettings.SpawnerData>>>();
    private final List<Processor<DimensionType>> dimensionTypeProcessors = new ArrayList<Processor<DimensionType>>();
    // Non-final: CraftmineRegistryMaterialization re-points the builder at the
    // rebuilt registry access so modified biomes resolve as registry references.
    private HolderLookup.Provider registries;
    private final List<BiomeModificationBuilder> changedBiomes = new ArrayList<BiomeModificationBuilder>();
    private ResourceKey<NoiseGeneratorSettings> baseGeneratorSettings = NoiseGeneratorSettings.OVERWORLD;
    private Optional<ChunkGeneratorGenerator> chunkGeneratorOverride = Optional.empty();
    private final List<Consumer<NoiseGeneratorSettingsBuilder>> noiseModifiers = new ArrayList<Consumer<NoiseGeneratorSettingsBuilder>>();
    private MineSpawnStrategy spawnStrategy = MineSpawnStrategy.SURFACE;

    public WorldGenBuilder(HolderLookup.Provider provider) {
        this.registries = provider;
    }

    /** Re-points the builder at a registry access containing the mine's materialized entries. */
    public WorldGenBuilder setRegistries(HolderLookup.Provider provider) {
        this.registries = provider;
        return this;
    }

    public HolderLookup.Provider registries() {
        return this.registries;
    }

    public <T> Holder<T> getOrThrow(ResourceKey<T> resourceKey) {
        return this.registries.getOrThrow(resourceKey);
    }

    public WorldGenBuilder addBiome(ResourceKey<Biome> resourceKey) {
        this.enabledBiomes.add(resourceKey);
        return this;
    }

    public WorldGenBuilder addMobSpawn(MobCategory mobCategory2, int n, MobSpawnSettings.SpawnerData spawnerData) {
        this.enabledMobs.computeIfAbsent(mobCategory2, mobCategory -> new ArrayList()).add(new Weighted<MobSpawnSettings.SpawnerData>(spawnerData, n));
        return this;
    }

    public WorldGenBuilder changeDimensionType(Processor<DimensionType> processor) {
        this.dimensionTypeProcessors.add(processor);
        return this;
    }

    public WorldGenBuilder changeBiome(ResourceKey<Biome> resourceKey, Consumer<BiomeModificationBuilder> consumer) {
        return this.changeBiomes(biomeModificationBuilder -> consumer.accept(biomeModificationBuilder.thatMatch(reference -> reference.key() == resourceKey)));
    }

    public WorldGenBuilder changeBiomes(Consumer<BiomeModificationBuilder> consumer) {
        BiomeModificationBuilder biomeModificationBuilder = new BiomeModificationBuilder();
        consumer.accept(biomeModificationBuilder);
        this.changedBiomes.add(biomeModificationBuilder);
        return this;
    }

    public WorldGenBuilder setNoiseGenerationBase(ResourceKey<NoiseGeneratorSettings> resourceKey) {
        this.baseGeneratorSettings = resourceKey;
        return this;
    }

    public WorldGenBuilder changeNoiseGeneration(Consumer<NoiseGeneratorSettingsBuilder> consumer) {
        this.noiseModifiers.add(consumer);
        return this;
    }

    public WorldGenBuilder withSpawnStrategy(MineSpawnStrategy mineSpawnStrategy) {
        this.spawnStrategy = mineSpawnStrategy;
        return this;
    }

    public WorldGenBuilder withCustomChunkGenerator(ChunkGeneratorGenerator chunkGeneratorGenerator) {
        this.chunkGeneratorOverride = Optional.of(chunkGeneratorGenerator);
        return this;
    }

    /**
     * DIFF-01: the fork's WorldGenBuilder (fce.a) stored its identity hash on the
     * mine's noise settings as the per-craft seed; the fork's RandomState then
     * XORed worldSeed ^ seed, so every crafted mine rolled its own terrain.
     * Returns the same value (builder identity hash) that createChunkGenerator
     * hands to custom generators; DimensionGenerator registers it per stem so
     * ChunkMapMixin can apply the fork's XOR for this mine's RandomState.
     */
    public long craftSeed() {
        return (long)this.hashCode();
    }

    public ChunkGenerator createChunkGenerator(String string) {
        HolderGetter<Biome> holderGetter = this.registries.lookupOrThrow(Registries.BIOME);
        List<ModifiedBiome> list = this.createModifiedBiomes((HolderLookup<Biome>)holderGetter, string);
        Map<ResourceKey<Biome>, ResourceKey<Biome>> map = list.stream().collect(Collectors.toMap(ModifiedBiome::original, ModifiedBiome::modified));
        // The modified (<level>/...) biomes only exist in the reloaded datapack registries later;
        // at eager build time resolve them from the in-memory BiomeBuilder output via direct holders
        // (the original snapshot ran this method post-reload, where the lookup already had them).
        Map<ResourceKey<Biome>, Holder<Biome>> modifiedHolders = new HashMap<ResourceKey<Biome>, Holder<Biome>>();
        HolderLookup<Biome> biomeLookup = this.registries.lookupOrThrow(Registries.BIOME);
        for (ModifiedBiome modifiedBiome : list) {
            // Prefer the registry reference materialized by CraftmineRegistryMaterialization
            // (the saved JSON then references the biome by key and the client's chunk
            // palettes stay resolvable); fall back to a direct holder when the biome is
            // not registered yet (eager build before materialization).
            Holder<Biome> resolved = biomeLookup.get(modifiedBiome.modified()).map(existing -> (Holder<Biome>) existing).orElseGet(() -> Holder.direct(modifiedBiome.biome()));
            modifiedHolders.put(modifiedBiome.modified(), resolved);
        }
        Map<Holder<Biome>, Holder<Biome>> map2 = new HashMap<Holder<Biome>, Holder<Biome>>();
        for (ModifiedBiome modifiedBiome : list) {
            map2.put(holderGetter.getOrThrow(modifiedBiome.original()), modifiedHolders.get(modifiedBiome.modified()));
        }
        com.google.common.collect.ImmutableList.Builder<Pair<Climate.ParameterPoint, Holder<Biome>>> parameterList = com.google.common.collect.ImmutableList.builder();
        // Fork parity (dot$a.c "overworld" preset -> dov): mines do NOT use vanilla's
        // OverworldBiomeBuilder - the snapshot ships a forked builder whose
        // pickShatteredBiome table has the two HOT temperature rows replaced by the
        // nether (row 3) and end (row 4) biome sets, so mines grow nether/end patches
        // at (mid..far inland, erosion band 5) strips in warm/hot regions. Vanilla's
        // builder leaves those rows null -> its list contains NO nether/end points at
        // all -> the filter below dropped every such pair and NETHER_BARRENS /
        // NETHER_FORESTS / END effects produced mines without a single nether/end
        // biome (the "small but deadly: no nether biomes even at 20 chunks" report;
        // same pipeline stage that made desert biomes grass-only before the
        // MaterialRuleBiomeMapper HolderHolder fix). MineOverworldBiomeBuilder =
        // 26.3's builder transcribed + the dov graft; the containsValue filter then
        // reproduces the fork's dot preset filter (grafted points survive only when
        // the mine's effects enable those biomes, or when no biome effect is set).
        new dev.craftmine.level.biome.MineOverworldBiomeBuilder().addBiomes(pair -> parameterList.add(pair.mapSecond(resourceKey -> map2.getOrDefault(holderGetter.getOrThrow(resourceKey), holderGetter.getOrThrow(resourceKey)))));
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> filtered = parameterList.build().stream()
            .filter(pair -> map2.containsValue(pair.getSecond()) || this.enabledBiomes.isEmpty())
            .toList();
        MultiNoiseBiomeSource multiNoiseBiomeSource = MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(filtered));
        HolderGetter<NoiseGeneratorSettings> holderGetter2 = this.registries.lookupOrThrow(Registries.NOISE_SETTINGS);
        NoiseGeneratorSettingsBuilder builder = new NoiseGeneratorSettingsBuilder(holderGetter2.getOrThrow(this.baseGeneratorSettings).value());
        this.noiseModifiers.forEach(consumer -> consumer.accept(builder));
        builder.modifySurfaceRule(rule -> MaterialRuleBiomeMapper.mapBiomes(rule, map, holderGetter, modifiedHolders)).salt(string.hashCode());
        Holder<NoiseGeneratorSettings> holder = Holder.direct(builder.create());
        if (this.chunkGeneratorOverride.isPresent()) {
            // Task 38 (fork fce.a 1:1): seed = (long)this.hashCode() - the fork's
            // identity hash of the WorldGenBuilder, giving each crafted mine its own
            // deterministic-at-craft-time RNG input (persisted via the generator JSON).
            return (ChunkGenerator)this.chunkGeneratorOverride.get().apply(this.registries, multiNoiseBiomeSource, holder, this.craftSeed());
        }
        return new NoiseBasedChunkGenerator((BiomeSource)multiNoiseBiomeSource, holder);
    }

    public Optional<DimensionType> createDimensionType(DimensionType dimensionType) {
        if (this.dimensionTypeProcessors.isEmpty()) {
            return Optional.empty();
        }
        DimensionType dimensionType2 = dimensionType;
        for (Processor<DimensionType> processor : this.dimensionTypeProcessors) {
            dimensionType2 = (DimensionType)processor.apply(dimensionType2);
        }
        return dimensionType2.equals(dimensionType) ? Optional.empty() : Optional.of(dimensionType2);
    }

    public List<ModifiedBiome> createModifiedBiomes(HolderLookup<Biome> holderLookup, String string) {
        Stream<Holder.Reference<Biome>> stream = this.enabledBiomes.isEmpty() ? holderLookup.listElements() : this.enabledBiomes.stream().map(holderLookup::getOrThrow);
        return stream.filter(reference -> !reference.key().identifier().getPath().startsWith("level")).map(reference -> {
            BiomeBuilder biomeBuilder = BiomeBuilder.fromVanilla(reference);
            for (Map.Entry<MobCategory, List<Weighted<MobSpawnSettings.SpawnerData>>> object2 : this.enabledMobs.entrySet()) {
                MobCategory mobCategory = object2.getKey();
                List<Weighted<MobSpawnSettings.SpawnerData>> list = object2.getValue();
                biomeBuilder.mobSpawnSettings().noSpawns(mobCategory);
                for (Weighted<MobSpawnSettings.SpawnerData> weighted : list) {
                    biomeBuilder.mobSpawnSettings().addSpawn(weighted.value().type(), mobCategory, weighted.weight(), weighted.value().count());
                }
            }
            this.changedBiomes.forEach(biomeModificationBuilder -> biomeModificationBuilder.apply(biomeBuilder));
            Biome biome = biomeBuilder.build();
            ResourceKey<Biome> resourceKey = biome.equals(reference.value()) ? reference.key() : ResourceKey.create(Registries.BIOME, reference.key().identifier().withPrefix(string + "/"));
            // Task 42 (mine exits): every mine biome builds into a fresh Biome
            // instance (Biome has identity equals), so each modified biome carries a
            // level-prefixed key (minecraft:<level>/<path>). The exit type rides the
            // per-mine BiomeBuilder (set by modifyExitType); placement-time lookups
            // resolve the MODIFIED id, so store it under that id here. Per-mine keys
            // keep two mines with different exit effects from overwriting each other.
            CraftmineExitType exitType = biomeBuilder.exitType();
            if (exitType != CraftmineExitType.NONE) {
                CraftmineBiomeExitTypes.set(resourceKey.identifier(), exitType);
            }
            return new ModifiedBiome(reference.key(), resourceKey, biome);
        }).filter(modifiedBiome -> modifiedBiome.original() != modifiedBiome.modified()).toList();
    }

    public MineSpawnStrategy spawnStrategy() {
        return this.spawnStrategy;
    }

    public static interface ChunkGeneratorGenerator {
        // Task 38 (fork fce.a 1:1): the fork's custom-generator lambda receives the
        // NoiseGeneratorSettings holder whose seed field carries (long)builder.hashCode()
        // (the fork stores it ON the settings record and reads it back via ekz.p();
        // 26.3's NoiseGeneratorSettings has no seed field, so the value is handed to
        // the lambda directly). grid_world uses it to seed its layout RNG so every
        // crafted grid mine gets its own spacing/width/y instead of a constant.
        ChunkGenerator apply(HolderLookup.Provider provider, BiomeSource biomeSource, Holder<NoiseGeneratorSettings> holder, long craftSeed);
    }

    public static interface Processor<T>
    extends Function<T, T> {
    }

    public record ModifiedBiome(ResourceKey<Biome> original, ResourceKey<Biome> modified, Biome biome) {
    }
}

