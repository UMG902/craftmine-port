package dev.craftmine.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import dev.craftmine.level.biome.CraftmineBiomeCopies;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Task 63 (FT-6 campsites in mines): the locate/presence path.
 * canCreateStructure builds its generation-point biome predicate as
 * structure.biomes()::contains and Structure.findValidGenerationPoint tests it
 * against biomes sampled from the level's biome source. For tag-gated vanilla
 * structures that set streams the startup registry's tag members, which never
 * include a mine's minecraft:levelN/<biome> copies (see CraftmineBiomeCopies)
 * - so /locate, explorer maps (camp maps!) and StructureCheck all reported
 * "not present" for tag-gated structures in mine dims even after the set
 * filter and placement registration were fixed. Re-route through
 * CraftmineBiomeCopies.copyAware: in mine dims the predicate set becomes
 * exactly the level's accepted possible biomes; everywhere else the
 * structure's own set is returned untouched. Exit structures already served
 * this consumer via their dynamic biomes().
 */
@Mixin(StructureCheck.class)
public abstract class StructureCheckMixin {
    @Shadow
    @Final
    private BiomeSource biomeSource;

    @Redirect(
        method = "canCreateStructure",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/Structure;biomes()Lnet/minecraft/core/HolderSet;"
        )
    )
    private HolderSet<Biome> craftmine$copyAwareBiomes(Structure structure) {
        return CraftmineBiomeCopies.copyAware(structure, this.biomeSource);
    }
}
