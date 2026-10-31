package dev.craftmine.level.biome;

import java.util.Map;
import dev.craftmine.level.biome.CraftmineExitType;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;

/**
 * Parallel storage for the craftmine biome exit-type field that 26.3's
 * BiomeSpecialEffects record no longer carries. Populated by worldgen
 * biome modifications; consumed by exit structure placement.
 *
 * Task 42: keys are biome IDs. Two key families exist:
 * - base ids (minecraft:plains) written by BiomeModificationBuilder.modifyExitType
 *   during WorldGenBuilder.createModifiedBiomes, and
 * - modified per-mine ids (minecraft:level1/plains) written by the
 *   WorldGenBuilder propagation right after the ModifiedBiome list is built.
 * Placement-time lookups (MineExitStructure.biomes / the
 * ChunkGeneratorStructureState mixin) only ever see the mine's actual biome
 * holders, which are the level-prefixed copies, so the modified-id entries are
 * the ones that matter. No fallback lookup on stripped ids: base entries of
 * other mines must never leak into this mine (the map is global server state).
 */
public final class CraftmineBiomeExitTypes {
    private CraftmineBiomeExitTypes() {
    }

    private static final Map<Identifier, CraftmineExitType> EXIT_TYPES = new ConcurrentHashMap<Identifier, CraftmineExitType>();

    public static void set(Identifier biomeId, CraftmineExitType exitType) {
        EXIT_TYPES.put(biomeId, exitType);
    }

    public static CraftmineExitType get(Identifier biomeId) {
        return EXIT_TYPES.getOrDefault(biomeId, CraftmineExitType.NONE);
    }

    /**
     * Exit type of the biome a placement predicate handed us. Only registry
     * references carry keys; direct holders appear only during eager craft-time
     * construction, never during structure placement, so they resolve to NONE.
     */
    public static CraftmineExitType getFor(Holder<Biome> biomeHolder) {
        if (biomeHolder instanceof Holder.Reference<Biome> reference) {
            return get(reference.key().identifier());
        }
        return CraftmineExitType.NONE;
    }
}
