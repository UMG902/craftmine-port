package dev.craftmine.level.biome;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;

import dev.craftmine.util.VanillaReflection;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

/**
 * Adds the craftmine "endish" multi-noise biome source preset (the end-flavored
 * parameter list used by 25w14craftmine's end-themed mines; ships as
 * worldgen/multi_noise_biome_source_parameter_list/endish.json).
 *
 * 26.3's Preset record has a public constructor, but BOTH the constructor's
 * second parameter type (the private SourceProvider interface) and the
 * private BY_NAME map are unnameable from outside the nest. This class therefore
 * builds the SourceProvider as a java.lang.reflect.Proxy and calls the
 * constructor reflectively, then extends BY_NAME through an accessor mixin.
 * The preset codec resolves through BY_NAME at parse time (flatXmap lambda
 * reads the live map), so the entry added pre-datapack-load resolves.
 */
public final class CraftmineMultiNoisePresets {
    private CraftmineMultiNoisePresets() {
    }

    public static final MultiNoiseBiomeSourceParameterList.Preset ENDISH = createEndishPreset();

    private static MultiNoiseBiomeSourceParameterList.Preset createEndishPreset() {
        try {
            Class<?> sourceProviderClass = Class.forName(
                "net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList$Preset$SourceProvider");
            Object sourceProvider = Proxy.newProxyInstance(
                CraftmineMultiNoisePresets.class.getClassLoader(),
                new Class<?>[]{sourceProviderClass},
                (proxy, method, args) -> {
                    // single abstract method: <T> Climate.ParameterList<T> apply(Function<ResourceKey<Biome>, T>)
                    @SuppressWarnings("unchecked")
                    Function<net.minecraft.resources.ResourceKey<Biome>, ?> function =
                        (Function<net.minecraft.resources.ResourceKey<Biome>, ?>) args[0];
                    return new Climate.ParameterList<>(List.of(
                        Pair.of(Climate.parameters(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f), function.apply(Biomes.THE_END)),
                        Pair.of(Climate.parameters(0.0f, -0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f), function.apply(Biomes.END_BARRENS)),
                        Pair.of(Climate.parameters(0.4f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f), function.apply(Biomes.END_MIDLANDS)),
                        Pair.of(Climate.parameters(0.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.375f), function.apply(Biomes.SMALL_END_ISLANDS)),
                        Pair.of(Climate.parameters(-0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.175f), function.apply(Biomes.END_HIGHLANDS))
                    ));
                });
            var ctor = MultiNoiseBiomeSourceParameterList.Preset.class
                .getDeclaredConstructor(Identifier.class, sourceProviderClass);
            ctor.setAccessible(true);
            return (MultiNoiseBiomeSourceParameterList.Preset)
                ctor.newInstance(Identifier.withDefaultNamespace("endish"), sourceProvider);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to build endish multi-noise preset", e);
        }
    }

    /** Referenced from CraftmineBootstrap.bootstrapAll - fields self-register on clinit. */
    public static void bootstrap() {
        byNameMap().put(ENDISH.id(), ENDISH);
    }

    /**
     * BY_NAME access by reflection. An accessor mixin cannot be used here: its
     * interface class cannot be loaded until the Preset target class loads
     * (lazy Knot transformation), but bootstrapAll runs BEFORE that.
     */
    @SuppressWarnings("unchecked")
    private static Map<Identifier, MultiNoiseBiomeSourceParameterList.Preset> byNameMap() {
        return (Map<Identifier, MultiNoiseBiomeSourceParameterList.Preset>)
            VanillaReflection.readStaticField(MultiNoiseBiomeSourceParameterList.Preset.class, "BY_NAME");
    }
}
