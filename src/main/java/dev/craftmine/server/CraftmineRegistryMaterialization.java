/*
 * Rebuilds the WORLD registry layer with a mine's runtime materialized entries
 * and installs the result on both holders that matter:
 *
 *  - TheGame.registries            (port code reads it: registryAccess() etc.)
 *  - MinecraftServer.registries    (26.3's configuration-phase
 *                                   SynchronizeRegistriesTask reads THIS to
 *                                   build the client registry sync payload)
 *
 * The snapshot achieved the same by tearing the whole game down and running a
 * WorldReloader pass over the freshly written datapack; 26.3 has no such
 * reload, so the layer is rebuilt in place: every WORLD-layer registry is
 * copied into a fresh MappedRegistry, the mine's modified biomes (+ optional
 * custom dimension type) are registered into the copies, tags are re-bound,
 * everything is frozen again.
 */
package dev.craftmine.server;

import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import dev.craftmine.server.TheGame;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import dev.craftmine.mines.WorldGenBuilder;
import org.slf4j.Logger;

public final class CraftmineRegistryMaterialization {
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private CraftmineRegistryMaterialization() {
    }

    /**
     * Rebuilds the WORLD layer with the given modified biomes and (optionally)
     * a custom dimension type under customDimTypeKey, then updates TheGame and
     * MinecraftServer. Returns the new composite access.
     */
    public static RegistryAccess.Frozen materialize(
        TheGame theGame,
        List<WorldGenBuilder.ModifiedBiome> modifiedBiomes,
        Optional<Map.Entry<ResourceKey<DimensionType>, DimensionType>> customDimensionType,
        String levelPath
    ) {
        MinecraftServer server = theGame.server();
        RegistryAccess.Frozen worldLayer = theGame.registries().getLayer(RegistryLayer.WORLD);

        Map<ResourceKey<? extends Registry<?>>, Registry<?>> rebuilt = new HashMap<>();
        int addedBiomes = 0;
        for (RegistryAccess.RegistryEntry<?> entry : worldLayer.registries().toList()) {
            Registry<?> registry = entry.value();
            if (entry.key() == Registries.BIOME) {
                rebuilt.put(entry.key(), rebuildBiomes((Registry<Biome>) registry, modifiedBiomes, levelPath));
                addedBiomes = modifiedBiomes.size();
            } else if (customDimensionType.isPresent() && entry.key() == Registries.DIMENSION_TYPE) {
                rebuilt.put(entry.key(), rebuildWithOne((Registry<DimensionType>) registry, customDimensionType.get().getKey(), customDimensionType.get().getValue()));
            } else {
                rebuilt.put(entry.key(), registry);
            }
        }

        RegistryAccess.Frozen newWorldLayer = new RegistryAccess.ImmutableRegistryAccess(rebuilt).freeze();
        net.minecraft.core.LayeredRegistryAccess<RegistryLayer> newAccess = theGame.registries().replaceFrom(RegistryLayer.WORLD, newWorldLayer);

        theGame.craftmine$updateRegistries(newAccess);
        ((dev.craftmine.mixin.MinecraftServerAccessor) (Object) server).craftmine$setRegistries(newAccess);

        LOGGER.info("craftmine-materialize: materialized {} biome(s){} into registry generation {}",
            addedBiomes,
            customDimensionType.isPresent() ? " + dimension type " + customDimensionType.get().getKey().identifier() : "",
            theGame.craftmine$registryGeneration());
        CraftmineClientResync.markOnlinePlayersStale(server);
        return newAccess.compositeAccess();
    }

    private static Registry<Biome> rebuildBiomes(Registry<Biome> oldRegistry, List<WorldGenBuilder.ModifiedBiome> modifiedBiomes, String levelPath) {
        MappedRegistry<Biome> rebuilt = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        for (Map.Entry<ResourceKey<Biome>, Biome> entry : oldRegistry.entrySet()) {
            rebuilt.register(entry.getKey(), entry.getValue(), oldRegistry.registrationInfo(entry.getKey()).orElse(RegistrationInfo.BUILT_IN));
        }
        RegistrationInfo runtimeInfo = new RegistrationInfo(Optional.empty(), Lifecycle.stable());
        for (WorldGenBuilder.ModifiedBiome modifiedBiome : modifiedBiomes) {
            rebuilt.register(modifiedBiome.modified(), modifiedBiome.biome(), runtimeInfo);
        }
        // Task 36: bind the mine datapack's biome tags (BugMine "Apply Level Biome
        // Tags"). The tag JSONs written by DimensionGenerator.writeBiomeTags are only
        // loaded from disk on the next world load; during the live session the mine's
        // biomes sat in NO tag, so structures could never generate in crafted mines
        // (the mine's biome source only produces the modified minecraft:levelN/<biome>
        // entries) and biome-driven behaviors (spawn variants, sieges, Dry Land) were
        // inert. The original snapshot got this for free from its WorldReloader
        // datapack reload. Vanilla tag merging is additive across sources, so the
        // copied vanilla tag holders and the mine's entries are UNIONED.
        Map<TagKey<Biome>, List<Holder<Biome>>> tags = collectTags(oldRegistry, rebuilt);
        for (Map.Entry<TagKey<Biome>, List<Holder<Biome>>> tag : collectMineBiomeTags(rebuilt, modifiedBiomes, levelPath).entrySet()) {
            tags.computeIfAbsent(tag.getKey(), key -> new ArrayList<Holder<Biome>>()).addAll(tag.getValue());
        }
        if (!tags.isEmpty()) {
            rebuilt.bindTags(tags);
        }
        return rebuilt.freeze();
    }

    private static <T> Registry<T> rebuildWithOne(Registry<T> oldRegistry, ResourceKey<T> newKey, T newValue) {
        MappedRegistry<T> rebuilt = new MappedRegistry<>(oldRegistry.key(), Lifecycle.stable());
        for (Map.Entry<ResourceKey<T>, T> entry : oldRegistry.entrySet()) {
            rebuilt.register(entry.getKey(), entry.getValue(), oldRegistry.registrationInfo(entry.getKey()).orElse(RegistrationInfo.BUILT_IN));
        }
        rebuilt.register(newKey, newValue, new RegistrationInfo(Optional.empty(), Lifecycle.stable()));
        Map<TagKey<T>, List<Holder<T>>> tags = collectTags(oldRegistry, rebuilt);
        if (!tags.isEmpty()) {
            rebuilt.bindTags(tags);
        }
        return rebuilt.freeze();
    }

    /** Collects every tag of the old registry onto the rebuilt one (holders resolved by key). */
    private static <T> Map<TagKey<T>, List<Holder<T>>> collectTags(Registry<T> oldRegistry, MappedRegistry<T> target) {
        Map<TagKey<T>, List<Holder<T>>> tags = new HashMap<>();
        oldRegistry.getTags().forEach(named -> {
            // MUST be mutable: rebuildBiomes UNIONs the mine's own tag entries into
            // these lists via computeIfAbsent().addAll() (Stream.toList() returns an
            // unmodifiable list -> UnsupportedOperationException on the first mine
            // whose tag key collides with a vanilla tag, i.e. ALWAYS). This threw for
            // every mine crafted on builds 36-38 before anyone re-tested mine crafting.
            List<Holder<T>> holders = named.stream()
                .map(holder -> {
                    if (holder instanceof Holder.Reference<T> reference) {
                        return target.get(reference.key()).map(existing -> (Holder<T>) existing).orElse(holder);
                    }
                    return holder;
                })
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            tags.put(named.key(), holders);
        });
        return tags;
    }

    /**
     * Resolves the mine datapack's biome tags (DimensionGenerator.BIOME_TAGS, 1:1
     * with BugMine's "Apply Level Biome Tags" fix) against the rebuilt registry.
     * Only entries whose modified biome was actually materialized are included,
     * mirroring exactly what a datapack reload of the written tag JSONs binds.
     */
    private static Map<TagKey<Biome>, List<Holder<Biome>>> collectMineBiomeTags(
        Registry<Biome> registry,
        List<WorldGenBuilder.ModifiedBiome> modifiedBiomes,
        String levelPath
    ) {
        Map<TagKey<Biome>, List<Holder<Biome>>> result = new HashMap<>();
        Set<Identifier> modifiedIdentifiers = new HashSet<>();
        for (WorldGenBuilder.ModifiedBiome modifiedBiome : modifiedBiomes) {
            modifiedIdentifiers.add(modifiedBiome.modified().identifier());
        }
        for (String[] tag : dev.craftmine.server.DimensionGenerator.BIOME_TAGS) {
            List<Holder<Biome>> holders = null;
            for (String biome : tag[1].split(" ")) {
                Identifier id = Identifier.withDefaultNamespace(levelPath + "/" + biome);
                if (!modifiedIdentifiers.contains(id)) {
                    continue;
                }
                Optional<Holder.Reference<Biome>> holder = registry.get(id);
                if (holder.isPresent()) {
                    if (holders == null) {
                        holders = new ArrayList<Holder<Biome>>();
                    }
                    holders.add(holder.get());
                }
            }
            if (holders != null) {
                result.put(TagKey.create(Registries.BIOME, Identifier.withDefaultNamespace(tag[0])), holders);
            }
        }
        return result;
    }
}
