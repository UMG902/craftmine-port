/*
 * Exposes Biome internals for the craftmine BiomeBuilder (26.3 made the
 * Biome record components private and the constructor private).
 */
package dev.craftmine.mixin;

import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Biome.class)
public interface BiomeAccessor {
    @Accessor("climateSettings")
    Biome.ClimateSettings craftmine$getClimateSettings();

    @Invoker("<init>")
    static Biome craftmine$create(Biome.ClimateSettings climateSettings, EnvironmentAttributeMap attributes, BiomeSpecialEffects specialEffects, BiomeGenerationSettings generationSettings) {
        throw new AssertionError("mixin invoker");
    }
}
