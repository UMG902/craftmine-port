/*
 * Ported from 25w14craftmine (dev.craftmine.level.biome.ClimateSettings).
 * 26.3 keeps ClimateSettings as a nested record of Biome; this craftmine variant adds a
 * Builder and converts to the vanilla nested record for biome construction.
 */
package dev.craftmine.level.biome;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.biome.Biome;

public record ClimateSettings(boolean hasPrecipitation, float temperature, Biome.TemperatureModifier temperatureModifier, float downfall) {
    public static final MapCodec<ClimateSettings> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.BOOL.fieldOf("has_precipitation").forGetter(ClimateSettings::hasPrecipitation),
        Codec.FLOAT.fieldOf("temperature").forGetter(ClimateSettings::temperature),
        Biome.TemperatureModifier.CODEC.optionalFieldOf("temperature_modifier", Biome.TemperatureModifier.NONE).forGetter(ClimateSettings::temperatureModifier),
        Codec.FLOAT.fieldOf("downfall").forGetter(ClimateSettings::downfall)
    ).apply(instance, ClimateSettings::new));

    public Biome.ClimateSettings toVanilla() {
        return new Biome.ClimateSettings(this.hasPrecipitation, this.temperature, this.temperatureModifier, this.downfall);
    }

    public static ClimateSettings fromVanilla(Biome.ClimateSettings climateSettings) {
        return new ClimateSettings(climateSettings.hasPrecipitation(), climateSettings.temperature(), climateSettings.temperatureModifier(), climateSettings.downfall());
    }

    public Builder asBuilder() {
        return new Builder().hasPrecipitation(this.hasPrecipitation).temperature(this.temperature).temperatureAdjustment(this.temperatureModifier).downfall(this.downfall);
    }

    public static class Builder {
        private boolean hasPrecipitation;
        private float temperature;
        private Biome.TemperatureModifier temperatureModifier = Biome.TemperatureModifier.NONE;
        private float downfall;

        public Builder hasPrecipitation(boolean bl) {
            this.hasPrecipitation = bl;
            return this;
        }

        public boolean hasPrecipitation() {
            return this.hasPrecipitation;
        }

        public Builder temperature(float f) {
            this.temperature = f;
            return this;
        }

        public float temperature() {
            return this.temperature;
        }

        public Builder temperatureAdjustment(Biome.TemperatureModifier temperatureModifier) {
            this.temperatureModifier = temperatureModifier;
            return this;
        }

        public Biome.TemperatureModifier temperatureModifier() {
            return this.temperatureModifier;
        }

        public Builder downfall(float f) {
            this.downfall = f;
            return this;
        }

        public ClimateSettings build() {
            return new ClimateSettings(this.hasPrecipitation, this.temperature, this.temperatureModifier, this.downfall);
        }
    }
}
