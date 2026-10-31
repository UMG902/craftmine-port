/*
 * Task 63 (FT-6, campsites + camp maps in mines): makes tag-driven structure
 * biome tests work against mine biome copies.
 *
 * ROOT CAUSE (why no vanilla structure - not just camps - ever generated in
 * crafted mines): a structure JSON's "biomes" field parses into a
 * HolderSet.Named whose contents are bound by the biome registry instance
 * that loaded the structure-set datapack, i.e. the STARTUP world-layer biome
 * registry (R0). CraftmineRegistryMaterialization rebuilds the biome registry
 * per mine (R1) and binds the mine's tags there (task 36), but the structure
 * set registry is carried over BY REFERENCE, so every structure's Named
 * biome set keeps streaming R0's vanilla members. The mine's
 * minecraft:levelN/<biome> copies never appear in any streamed tag, so
 * ChunkGeneratorStructureState.hasBiomesForStructureSet drops every
 * tag-gated structure set from mine dims (only the exit sets survive, via
 * the task-42 mixin's exit-type re-route).
 *
 * The 25w14craftmine fork never hit this: its WorldReloader re-parsed ALL
 * worldgen datapacks after writing the mine's, so its structures were
 * re-created against a biome registry whose tags contained the mine's
 * copies (vanilla tag UNION mine tag). 26.3 has no re-parse, so this class
 * reproduces the fork's union semantics statically:
 *
 *   a mine copy minecraft:levelN/<biome> matches a structure biome set
 *   iff the set contains a member whose key path equals <biome> (the
 *   path tail after the last '/'); vanilla-keyed holders keep matching
 *   exactly as before.
 *
 * In dimensions whose possible biomes contain no copies (overworld, nether,
 * end, flat, hub) every consumer below returns the structure's own set and
 * vanilla runs byte-for-byte; the tail rule can only ever match holders
 * whose key path contains '/', which only mine copies have.
 *
 * All four 26.3 consumers of Structure.biomes() are routed through here:
 * - ChunkGeneratorStructureState.hasBiomesForStructureSet   (per-level set filter)
 * - ChunkGeneratorStructureState.lambda$generatePositions$0 (placement registration)
 * - ChunkGenerator.tryGenerateStructure                     (chunk-gen placement predicate)
 * - StructureCheck.canCreateStructure                       (locate/presence predicate)
 * (the task-42 note that counted three consumers missed tryGenerateStructure;
 * exit structures already served it through their overridden dynamic set).
 */
package dev.craftmine.level.biome;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.biome.Biome;

public final class CraftmineBiomeCopies {
    private CraftmineBiomeCopies() {
    }

    /**
     * True if any of the level's possible biomes is a mine copy
     * (minecraft:levelN/<biome> - detected by the '/' in its key path).
     */
    public static boolean hasCopies(java.util.Set<Holder<Biome>> possibleBiomes) {
        for (Holder<Biome> holder : possibleBiomes) {
            if (CraftmineBiomeCopies.isCopy(holder)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCopy(Holder<Biome> holder) {
        if (holder instanceof Holder.Reference<Biome> reference) {
            return reference.key().identifier().getPath().indexOf('/') >= 0;
        }
        return false;
    }

    /**
     * The vanilla biome names a structure accepts, as key paths of the
     * members of its (static, startup-registry-bound) biomes set.
     */
    public static Set<String> acceptedPaths(Structure structure) {
        HashSet<String> accepted = new HashSet<String>();
        structure.biomes().stream().forEach(holder -> {
            if (holder instanceof Holder.Reference<Biome> reference) {
                accepted.add(reference.key().identifier().getPath());
            }
        });
        return accepted;
    }

    /**
     * Fork union semantics: exact path match, or - for copy holders only -
     * the path tail after the last '/' matches an accepted vanilla name.
     */
    public static boolean matches(Holder<Biome> holder, Set<String> accepted) {
        if (!(holder instanceof Holder.Reference<Biome> reference)) {
            return false;
        }
        String path = reference.key().identifier().getPath();
        if (accepted.contains(path)) {
            return true;
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 && accepted.contains(path.substring(slash + 1));
    }

    /**
     * Copy-aware Structure.biomes() for the tryGenerateStructure and
     * canCreateStructure consumers: in mine dims returns exactly the level's
     * possible biomes the structure accepts (the sampled holders those
     * predicates test are possibleBiomes members, so direct-set identity
     * holds); everywhere else the structure's own set, untouched.
     *
     * Task 64 (eye-of-exit regression): structures with a DYNAMIC biomes() set
     * - the mine-exit structures, whose contains() tests the biome's craftmine
     * exit type and whose stream() is intentionally empty - must keep their
     * own set. Deriving "accepted" from stream() yielded the EMPTY set, so
     * from task 63 on, mine exits failed the biome test in every mine dim:
     * they stopped generating (ChunkGenerator.tryGenerateStructure) and
     * stopped resolving for the exit eye / locate (StructureCheck
     * .canCreateStructure) - the eye of exit damaged the player but never
     * spawned its flying entity (ExitEyeItem returns CONSUME when the search
     * finds nothing). The two stream()-based consumers already special-case
     * exit structures in ChunkGeneratorStructureStateMixin; the two
     * contains()-based consumers are special-cased here.
     */
    public static HolderSet<Biome> copyAware(Structure structure, BiomeSource biomeSource) {
        if (structure instanceof dev.craftmine.worldgen.MineExitStructure) {
            return structure.biomes();
        }
        Set<Holder<Biome>> possible = biomeSource.possibleBiomes();
        if (!CraftmineBiomeCopies.hasCopies(possible)) {
            return structure.biomes();
        }
        Set<String> accepted = CraftmineBiomeCopies.acceptedPaths(structure);
        List<Holder<Biome>> matching = possible.stream().filter(holder -> CraftmineBiomeCopies.matches(holder, accepted)).toList();
        return HolderSet.direct(matching);
    }
}
