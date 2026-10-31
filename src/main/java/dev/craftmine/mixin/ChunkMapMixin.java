package dev.craftmine.mixin;

import dev.craftmine.registry.CraftmineLevelStemData;
import dev.craftmine.registry.CraftmineRegistryHelpers;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * DIFF-01 (PARITY.md): per-mine terrain noise seeding.
 *
 * The fork's RandomState (eli) constructor computed worldSeed ^ settings.p(),
 * where the settings' extra long seed field (fork ekz, accessor p()) was set by
 * its WorldGenBuilder (fce.a) to the builder's identity hash at craft time.
 * Result: every crafted mine - noise or grid - rolled its own terrain even with
 * an identical effect configuration. 26.3's RandomState.create uses the raw
 * level seed for every dimension, so all mines of a world shared the world seed
 * (same-config mines = identical terrain).
 *
 * 26.3 creates the level's RandomState exactly once, here in the ChunkMap
 * constructor (NoiseBasedChunkGenerator branch for noise generators, the
 * fallback branch for everything else). We redirect both creation sites and XOR
 * the seed with the mine's per-craft seed from CraftmineLevelStemData - exactly
 * the fork's eli ctor semantics. Non-mine levels (overworld/hub/vanilla dims)
 * have no stem entry, craftSeed() = 0, XOR is a no-op - matching the fork,
 * whose vanilla settings carried no seed either. Structure placement (the
 * levelSeed passed on to createState) is intentionally left untouched: the fork
 * only XORed inside RandomState, not the structure RNG.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/core/HolderGetter;JLnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;)Lnet/minecraft/world/level/levelgen/RandomState;"
        )
    )
    private RandomState craftmine$createWithMineSeed(HolderGetter<NormalNoise> noises, long seed, NoiseGeneratorSettings settings, ServerLevel level) {
        return RandomState.create(noises, craftmine$levelSeed(level, seed), settings);
    }

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/core/HolderGetter;JZLnet/minecraft/world/level/block/state/BlockState;ILnet/minecraft/world/level/levelgen/NoiseRouter;)Lnet/minecraft/world/level/levelgen/RandomState;"
        )
    )
    private RandomState craftmine$createFallbackWithMineSeed(HolderGetter<NormalNoise> noises, long seed, boolean useLegacyRandom, BlockState defaultBlock, int seaLevel, NoiseRouter noiseRouter, ServerLevel level) {
        return RandomState.create(noises, craftmine$levelSeed(level, seed), useLegacyRandom, defaultBlock, seaLevel, noiseRouter);
    }

    private static long craftmine$levelSeed(ServerLevel level, long seed) {
        ResourceKey<net.minecraft.world.level.dimension.LevelStem> stemKey = CraftmineRegistryHelpers.levelToLevelStem(level.dimension());
        return seed ^ CraftmineLevelStemData.get(stemKey).craftSeed();
    }
}
