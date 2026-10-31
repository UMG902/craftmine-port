/*
 * Ported from 25w14craftmine's SurfaceRules.mapBiomes additions.
 * 26.3 renamed the surface rule system to material rules, so the deep-remap
 * is implemented as a static walker over the MaterialRule/MaterialCondition trees.
 */
package dev.craftmine.worldgen;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.material.condition.BiomeCondition;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.condition.NotCondition;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;

public final class MaterialRuleBiomeMapper {
    private MaterialRuleBiomeMapper() {
    }

    public static MaterialRule mapBiomes(MaterialRule rule, Map<ResourceKey<Biome>, ResourceKey<Biome>> map, HolderGetter<Biome> biomes) {
        return mapBiomes(rule, map, biomes, Map.of());
    }

    public static MaterialRule mapBiomes(MaterialRule rule, Map<ResourceKey<Biome>, ResourceKey<Biome>> map, HolderGetter<Biome> biomes, Map<ResourceKey<Biome>, Holder<Biome>> modifiedHolders) {
        if (rule instanceof MaterialRule.HolderHolder holderHolder) {
            // 26.3 data-driven material rules: every named subtree (BIOME_SURFACE,
            // UNDER_BIOME_SURFACE, FROZEN_PEAKS_SURFACE, SAND_OR_SANDSTONE_IF_CEILING, ...)
            // is registerAndWrap'ped into a HolderHolder that just delegates to the
            // registered rule. Unwrap it, remap the inner tree, and re-wrap - otherwise
            // NONE of the per-biome branches (desert sand, snowy, badlands, podzol,
            // mycelium, ...) ever gets its biome conditions remapped and every mine
            // surface falls through to the default grass/dirt (the user's "desert
            // biomes are just plains ones with the biome set to desert").
            MaterialRule inner = holderHolder.holder().value();
            MaterialRule mapped = mapBiomes(inner, map, biomes, modifiedHolders);
            return mapped == inner ? rule : new MaterialRule.HolderHolder(Holder.direct(mapped));
        }
        if (rule instanceof SequenceRule sequenceRule) {
            List<MaterialRule> mapped = sequenceRule.sequence().stream()
                .map(r -> mapBiomes(r, map, biomes, modifiedHolders)).collect(Collectors.toList());
            return new SequenceRule(mapped);
        }
        if (rule instanceof ConditionRule conditionRule) {
            return new ConditionRule(mapBiomes(conditionRule.ifTrue(), map, biomes, modifiedHolders), mapBiomes(conditionRule.thenRun(), map, biomes, modifiedHolders));
        }
        return rule;
    }

    public static MaterialCondition mapBiomes(MaterialCondition condition, Map<ResourceKey<Biome>, ResourceKey<Biome>> map, HolderGetter<Biome> biomes) {
        return mapBiomes(condition, map, biomes, Map.of());
    }

    public static MaterialCondition mapBiomes(MaterialCondition condition, Map<ResourceKey<Biome>, ResourceKey<Biome>> map, HolderGetter<Biome> biomes, Map<ResourceKey<Biome>, Holder<Biome>> modifiedHolders) {
        if (condition instanceof MaterialCondition.HolderHolder holderHolder) {
            // Same registry-wrap as MaterialRule.HolderHolder (e.g. VanillaMaterialConditions
            // DEEP_UNDER_FLOOR / VERY_DEEP_UNDER_FLOOR referenced via getCondition) - unwrap,
            // remap, re-wrap.
            MaterialCondition inner = holderHolder.holder().value();
            MaterialCondition mapped = mapBiomes(inner, map, biomes, modifiedHolders);
            return mapped == inner ? condition : new MaterialCondition.HolderHolder(Holder.direct(mapped));
        }
        if (condition instanceof BiomeCondition biomeCondition) {
            List<Holder<Biome>> remapped = biomeCondition.biomes().stream().map(holder -> {
                ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
                if (key == null) {
                    return holder;
                }
                ResourceKey<Biome> replaced = map.getOrDefault(key, key);
                if (replaced == key) {
                    // Untouched biome: RE-RESOLVE from the current lookup instead of
                    // keeping the original holder. The vanilla material-rule subtrees
                    // were bootstrapped against the static BuiltInRegistries biome
                    // instances, but the mine's dimension JSON is encoded (and the
                    // generator runs) against the materialized world view (see
                    // DimensionGenerator: registryOps = materializedAccess) - a
                    // static-bound reference fails that registry-set validation
                    // ("Element Reference{...} is not valid in current registry set",
                    // 2026-10-31 AF probe) the moment a remapped subtree serializes
                    // INLINE (HolderHolder re-wrap below) instead of by registry
                    // reference. AE never hit this because unremapped HolderHolder
                    // subtrees serialized by reference and were never descended into.
                    // Resolving every holder from the same view as the generator's
                    // biome source also makes runtime BiomeCondition matching
                    // identity-consistent (same cached Reference per key).
                    return biomes.getOrThrow(key);
                }
                // modified biomes are not in the static lookup at eager build time
                Holder<Biome> modifiedHolder = modifiedHolders.get(replaced);
                return modifiedHolder != null ? modifiedHolder : biomes.getOrThrow(replaced);
            }).collect(Collectors.toList());
            return new BiomeCondition(HolderSet.direct(remapped));
        }
        if (condition instanceof NotCondition notCondition) {
            return new NotCondition(mapBiomes(notCondition.target(), map, biomes, modifiedHolders));
        }
        return condition;
    }
}
