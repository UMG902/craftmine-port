package dev.craftmine.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import dev.craftmine.level.biome.CraftmineBiomeCopies;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Task 63 (FT-6 campsites in mines): the chunk-gen placement path. When a
 * structure-candidate chunk actually generates its structures,
 * tryGenerateStructure builds the biome predicate as
 * structure.biomes()::contains and hands it to Structure.generate. For
 * tag-gated vanilla structures that set streams the startup registry's tag
 * members, which never include a mine's minecraft:levelN/<biome> copies
 * (see CraftmineBiomeCopies) - so even once a structure set survived the
 * per-level filter, every placement in a mine dim failed the biome test.
 * Re-route through CraftmineBiomeCopies.copyAware: in mine dims the predicate
 * set becomes exactly the level's accepted possible biomes (direct holders,
 * identity-compatible with the biome-source samples Structure.generate
 * tests); everywhere else the structure's own set is returned untouched.
 * Task 64: copyAware passes dynamic-biomes structures (mine exits) through
 * unchanged - deriving their "accepted" set from stream() yielded EMPTY and
 * silently stopped all exit generation in mines.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Shadow
    @Final
    protected BiomeSource biomeSource;

    @Redirect(
        method = "tryGenerateStructure",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/Structure;biomes()Lnet/minecraft/core/HolderSet;"
        )
    )
    private HolderSet<Biome> craftmine$copyAwareBiomes(Structure structure) {
        return CraftmineBiomeCopies.copyAware(structure, this.biomeSource);
    }
}
