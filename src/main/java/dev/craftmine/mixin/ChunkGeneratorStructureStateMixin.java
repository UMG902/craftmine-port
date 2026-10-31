package dev.craftmine.mixin;

import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import dev.craftmine.level.biome.CraftmineBiomeCopies;
import dev.craftmine.level.biome.CraftmineBiomeExitTypes;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import dev.craftmine.worldgen.MineExitStructure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Task 42 (mine exits): the 25w14craftmine fork filtered structure biomes by
 * the biome's exit type (fork evu.a(jl) called from its ChunkGenerator) before
 * every placement decision. 26.3's vanilla Structure.biomes() is a static
 * holder set with four consumers (hasBiomesForStructureSet,
 * lambda$generatePositions$0, ChunkGenerator.tryGenerateStructure,
 * StructureCheck.canCreateStructure - task 63 corrected the earlier count of
 * three); MineExitStructure serves the latter two via its overridden
 * biomes().contains, and the two stream() consumers here are re-routed below:
 * instead of streaming a static holder set, we stream the LEVEL's own
 * possible-biomes set filtered to biomes whose craftmine exit type matches
 * the structure's required_exit_type. The streamed holders are the same
 * instances the vanilla anyMatch(possibleBiomes::contains) test compares
 * against, so vanilla logic proceeds unchanged for exit structures, and every
 * non-exit structure keeps the vanilla path byte-for-byte.
 *
 * Task 63 (FT-6 campsites in mines): tag-gated vanilla structures (abandoned
 * camps, desert pyramids, ... - every structure whose biome set streams the
 * startup registry's tag members) were still dropped in mine dims, because
 * their Named biome sets stay bound to the startup biome registry while the
 * mine's minecraft:levelN/<biome> copies live on the materialized registry
 * (see CraftmineBiomeCopies for the full root cause). When the level's
 * possible biomes contain copies, non-exit entries are tested with the fork's
 * union semantics (copy tail matches a vanilla member path) instead of the
 * dead R0-tag identity test; dimensions without copies keep the vanilla path.
 */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class ChunkGeneratorStructureStateMixin {
    @Shadow
    @Final
    private BiomeSource biomeSource;

    /**
     * createForNormal/createForFlat filter the structure-set registry once per
     * level through this helper; without this hook a mine-exit structure set
     * (whose biomes() dynamic set streams empty) would be dropped before any
     * placement could ever run, and (task 63) every tag-gated vanilla set
     * would be dropped from mine dims (startup-registry tags vs copies).
     */
    @Inject(method = "hasBiomesForStructureSet", at = @At("HEAD"), cancellable = true)
    private static void craftmine$exitAwareSetFilter(StructureSet structureSet, BiomeSource biomeSource, CallbackInfoReturnable<Boolean> cir) {
        boolean hasExitStructures = false;
        for (StructureSet.StructureSelectionEntry entry : structureSet.structures()) {
            if (entry.structure().value() instanceof MineExitStructure) {
                hasExitStructures = true;
                break;
            }
        }
        if (!hasExitStructures && !CraftmineBiomeCopies.hasCopies(biomeSource.possibleBiomes())) {
            return;
        }
        Set<Holder<Biome>> possible = biomeSource.possibleBiomes();
        boolean hasCopies = CraftmineBiomeCopies.hasCopies(possible);
        boolean any = false;
        for (StructureSet.StructureSelectionEntry entry : structureSet.structures()) {
            Structure structure = entry.structure().value();
            if (structure instanceof MineExitStructure exit) {
                if (possible.stream().anyMatch(holder -> CraftmineBiomeExitTypes.getFor(holder) == exit.requiredExitType())) {
                    any = true;
                    break;
                }
            } else if (hasCopies) {
                // Task 63: mine copies never match the startup-registry tag
                // members by identity; match by name (fork union semantics).
                Set<String> accepted = CraftmineBiomeCopies.acceptedPaths(structure);
                if (possible.stream().anyMatch(holder -> CraftmineBiomeCopies.matches(holder, accepted))) {
                    any = true;
                    break;
                }
            } else if (structure.biomes().stream().anyMatch(possible::contains)) {
                any = true;
                break;
            }
        }
        cir.setReturnValue(any);
    }

    /**
     * generatePositions decides per structure whether its placement grid is
     * registered for this level. For exit structures, hand vanilla a direct
     * holder set of exactly the level's biomes that match the required exit
     * type - the stream elements are then possibleBiomes members, so the
     * following anyMatch(possibleBiomes::contains) is simply "this mine has a
     * biome of the required exit type". Task 63: in mine dims, tag-gated
     * vanilla structures get the same treatment with the fork's union
     * name-matching instead of exit types.
     *
     * NOTE: in the 26.3 server jar javac moved generatePositions' whole body
     * into the synthetic lambda lambda$generatePositions$0 (the per-structure-set
     * Consumer passed to forEach) - the Structure.biomes() call only exists
     * there, not in generatePositions itself (Gate 3 cannot catch this: the
     * @At target IS in the class constant pool, just inside another method).
     */
    @Redirect(
        method = "lambda$generatePositions$0",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/Structure;biomes()Lnet/minecraft/core/HolderSet;"
        )
    )
    private HolderSet<Biome> craftmine$exitAwareBiomes(Structure structure) {
        if (structure instanceof MineExitStructure exit) {
            Set<Holder<Biome>> possible = this.biomeSource.possibleBiomes();
            List<Holder<Biome>> matching = possible.stream().filter(holder -> CraftmineBiomeExitTypes.getFor(holder) == exit.requiredExitType()).toList();
            return HolderSet.direct(matching);
        }
        return CraftmineBiomeCopies.copyAware(structure, this.biomeSource);
    }
}
