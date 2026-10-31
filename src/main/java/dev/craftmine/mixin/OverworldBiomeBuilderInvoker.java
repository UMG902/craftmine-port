package dev.craftmine.mixin;

import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes OverworldBiomeBuilder.addBiomes (package-private in 26.3) to craftmine
 * world-gen code. Replaces the former same-package shim class, which only worked
 * while it lived inside net.minecraft.world.level.biome.
 */
@Mixin(OverworldBiomeBuilder.class)
public interface OverworldBiomeBuilderInvoker {
	@Invoker("addBiomes")
	void craftmine$invokeAddBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer);
}
