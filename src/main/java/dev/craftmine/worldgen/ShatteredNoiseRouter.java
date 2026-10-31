package dev.craftmine.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The "Shattered Blocks" world type's noise router (fork {@code elb.a} with
 * {@code (false, true)} — batch AG ground-truth audit of the snapshot's
 * {@code fcd.g}).
 *
 * <p>1.21.5's {@code NoiseRouterData.overworld(HolderGetter, HolderGetter,
 * boolean amplified, boolean shattered)} had a fourth boolean that every vanilla
 * call site passed {@code false}: when set, the router's final density is built
 * WITHOUT the slide/blend/noodle/beardifier post-processing and the underground
 * density branch is NEGATED. The craftmine snapshot flips exactly that boolean
 * ({@code fcd.g}: {@code elb.a(densityFunctions, noises, false, true)}) — the
 * world type is NOT amplified terrain. The earlier port misread the two booleans
 * and built {@code AMPLIFIED_OVERWORLD_FUNCTIONS} here, producing amplified
 * mountains instead of shattered blocks.</p>
 *
 * <p>Fork final density ({@code elb} line 219, {@code $$3=true}):
 * {@code rangeChoice(slopedCheese, -1000000, 1.5625, surfaceWithEntrances,
 * mul(underground(slopedCheese), -1))} — the normal surface below the cheese
 * threshold, and INVERTED underground density above it (solid where caves/air
 * would be), which is what yields the shattered floating-block look.</p>
 *
 * <p>26.3 rebuilt NoiseRouter as an 8-slot record and folded the aquifer noises
 * into {@link net.minecraft.world.level.levelgen.Aquifer.Config} and the ore
 * veins into the material rules, so every shared slot is taken verbatim from the
 * base (normal overworld) router this builder starts from; only the final
 * density is replaced. {@code underground} is private in 26.3 — the access
 * widener opens it (the same mechanism already used for the protected
 * {@code overworld} factory).</p>
 */
public final class ShatteredNoiseRouter {
    private ShatteredNoiseRouter() {
    }

    /** "overworld/sloped_cheese" — registered by 26.3's own density-function bootstrap. */
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE =
        ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace("overworld/sloped_cheese"));
    /** "overworld/caves/entrances" — registered by 26.3's own density-function bootstrap. */
    private static final ResourceKey<DensityFunction> ENTRANCES =
        ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace("overworld/caves/entrances"));

    public static NoiseRouter create(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise> noises, NoiseRouter base) {
        DensityFunction slopedCheese = NoiseRouterData.getFunction(functions, SLOPED_CHEESE);
        DensityFunction entrances = NoiseRouterData.getFunction(functions, ENTRANCES);
        // Fork $$16 = min(slopedCheese, mul(constant(5.0), entrances)) - the exact
        // surfaceWithEntrances expression 26.3's own overworld() builds at line 168.
        DensityFunction surfaceWithEntrances = DensityFunctions.min(slopedCheese, entrances.mul(5.0f));
        // Fork: the $$3=true branch of 1.21.5's initialDensityWithoutJaggedness -
        // mul(underground, constant(-1)). NoiseRouterData.underground is the same
        // function body (spaghetti2D/roughness/layer/cheese/pillars), opened by the
        // access widener.
        DensityFunction underground = NoiseRouterData.underground(functions, noises, slopedCheese);
        DensityFunction negatedUnderground = DensityFunctions.mul(underground, DensityFunctions.constant(-1.0f));
        // Fork $$17 = rangeChoice(slopedCheese, -1000000, 1.5625, surfaceWithEntrances,
        // negatedUnderground) - used RAW as the final density ($$3=true skips the
        // slide/blend/noodle/beardifier wrapper $$18).
        DensityFunction finalDensity = DensityFunctions.rangeChoice(slopedCheese, -1000000.0f, 1.5625f, surfaceWithEntrances, negatedUnderground);
        return new NoiseRouter(base.temperature(), base.vegetation(), base.continents(), base.erosion(),
            base.depth(), base.ridges(), base.chunkSurfaceLevel(), finalDensity);
    }
}
