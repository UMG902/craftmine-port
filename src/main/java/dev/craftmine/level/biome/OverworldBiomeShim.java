/*
 * Bridge: exposes OverworldBiomeBuilder.addBiomes (package-private in 26.3) to
 * craftmine world-gen code outside the biome package, via an @Invoker mixin
 * (OverworldBiomeBuilderInvoker) instead of same-package placement.
 */
package dev.craftmine.level.biome;

import com.mojang.datafixers.util.Pair;
import dev.craftmine.mixin.OverworldBiomeBuilderInvoker;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;

public final class OverworldBiomeShim {
    private OverworldBiomeShim() {
    }

    public static void addBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer) {
        ((OverworldBiomeBuilderInvoker) (Object) new OverworldBiomeBuilder()).craftmine$invokeAddBiomes(consumer);
    }
}
