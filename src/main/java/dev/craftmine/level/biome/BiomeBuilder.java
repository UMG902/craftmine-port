/*
 * Ported from 25w14craftmine (dev.craftmine.level.biome.BiomeBuilder).
 * Adapted to the 26.3 Biome record: climate via Biome.ClimateSettings (AW ctor),
 * mob spawns via the NATURAL_MOB_SPAWNS environment attribute.
 */
package dev.craftmine.level.biome;

import java.util.Objects;
import dev.craftmine.level.biome.ClimateSettings;
import dev.craftmine.level.biome.BiomeModificationBuilder;
import dev.craftmine.level.biome.CraftmineExitType;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.BiomeGenerationSettings.PlainBuilder;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;

public class BiomeBuilder {
    private final Biome base;
    @Nullable
    private net.minecraft.resources.Identifier baseBiomeId;
    @Nullable
    private ClimateSettings.Builder climateSettings;
    @Nullable
    private BiomeSpecialEffects.Builder specialEffects;
    @Nullable
    private MobSpawnSettings.Builder mobSpawnSettings;
    @Nullable
    private PlainBuilder generationSettings;
    private final java.util.List<java.util.function.Consumer<EnvironmentAttributeMap.Builder>> attributeModifiers = new java.util.ArrayList<>();
    /**
     * Task 42 (mine exits): per-builder craftmine exit type, set by
     * BiomeModificationBuilder.modifyExitType and read by WorldGenBuilder when
     * the mine's modified biomes are assembled. Lives on the builder (and then
     * on the modified biome id) instead of any global base-id map, so two mines
     * with different exit effects can never leak into each other.
     */
    private CraftmineExitType exitType = CraftmineExitType.NONE;

    private BiomeBuilder(Biome base) {
        this.base = base;
    }

    public CraftmineExitType exitType() {
        return this.exitType;
    }

    public BiomeBuilder exitType(CraftmineExitType craftmineExitType) {
        this.exitType = craftmineExitType == null ? CraftmineExitType.NONE : craftmineExitType;
        return this;
    }

    public static BiomeBuilder fromVanilla(Holder<Biome> biomeHolder) {
        Biome biome = biomeHolder.value();
        BiomeBuilder builder = new BiomeBuilder(biome);
        builder.baseBiomeId = biomeHolder.unwrapKey().map(key -> key.identifier()).orElse(null);
        ClimateSettings climate = ClimateSettings.fromVanilla(((dev.craftmine.mixin.BiomeAccessor) (Object) biome).craftmine$getClimateSettings());
        builder.climateSettings = climate.asBuilder();
        BiomeSpecialEffects effects = biome.getSpecialEffects();
        BiomeSpecialEffects.Builder effectsBuilder = new BiomeSpecialEffects.Builder();
        effectsBuilder.waterColor(effects.waterColor());
        effects.foliageColorOverride().ifPresent(effectsBuilder::foliageColorOverride);
        effects.dryFoliageColorOverride().ifPresent(effectsBuilder::dryFoliageColorOverride);
        effects.grassColorOverride().ifPresent(effectsBuilder::grassColorOverride);
        effectsBuilder.grassColorModifier(effects.grassColorModifier());
        builder.specialEffects = effectsBuilder;
        // 26.3: EnvironmentAttributeMap.get() is @Nullable — biomes without explicit
        // mob spawns (e.g. the_void) omit the NATURAL_MOB_SPAWNS entry entirely; the
        // attribute's registry default is MobSpawnSettings.EMPTY.
        EnvironmentAttributeMap.Entry<MobSpawnSettings, ?> spawnEntry = biome.getAttributes().get(EnvironmentAttributes.NATURAL_MOB_SPAWNS);
        MobSpawnSettings spawns = spawnEntry == null ? MobSpawnSettings.EMPTY : (MobSpawnSettings)(Object)spawnEntry.argument();
        MobSpawnSettings.Builder spawnsBuilder = new MobSpawnSettings.Builder();
        for (net.minecraft.world.entity.MobCategory category : net.minecraft.world.entity.MobCategory.values()) {
            var list = spawns.getMobsInCategory(category);
            if (list != null && !list.isEmpty()) {
                spawnsBuilder.addAllSpawns(category, list);
            }
        }
        builder.mobSpawnSettings = spawnsBuilder;
        PlainBuilder generationBuilder = new PlainBuilder();
        for (Holder<net.minecraft.world.level.levelgen.carver.WorldCarver> carver : biome.getGenerationSettings().getCarvers()) {
            generationBuilder.addCarver(carver);
        }
        int step = 0;
        for (var featureStep : biome.getGenerationSettings().features()) {
            for (Holder<PlacedFeature> feature : featureStep) {
                generationBuilder.addFeature(step, feature);
            }
            step++;
        }
        builder.generationSettings = generationBuilder;
        return builder;
    }

    public net.minecraft.resources.Identifier baseBiomeId() {
        return this.baseBiomeId;
    }

    public BiomeBuilder climate(ClimateSettings.Builder builder) {
        this.climateSettings = builder;
        return this;
    }

    public ClimateSettings.Builder climateSettings() {
        return Objects.requireNonNull(this.climateSettings);
    }

    public BiomeBuilder specialEffects(BiomeSpecialEffects.Builder builder) {
        this.specialEffects = builder;
        return this;
    }

    public BiomeSpecialEffects.Builder specialEffects() {
        return Objects.requireNonNull(this.specialEffects);
    }

    public BiomeBuilder mobSpawnSettings(MobSpawnSettings.Builder builder) {
        this.mobSpawnSettings = builder;
        return this;
    }

    public MobSpawnSettings.Builder mobSpawnSettings() {
        return Objects.requireNonNull(this.mobSpawnSettings);
    }

    public void modifyAttributes(java.util.function.Consumer<EnvironmentAttributeMap.Builder> consumer) {
        this.attributeModifiers.add(consumer);
    }

    public BiomeBuilder generationSettings(PlainBuilder plainBuilder) {
        this.generationSettings = plainBuilder;
        return this;
    }

    public PlainBuilder generationSettings() {
        return Objects.requireNonNull(this.generationSettings);
    }

    public Biome build() {
        if (this.climateSettings == null || this.specialEffects == null || this.mobSpawnSettings == null || this.generationSettings == null) {
            throw new IllegalStateException("You are missing parameters to build a proper biome\n" + String.valueOf(this));
        }
        Biome.ClimateSettings climate = this.climateSettings.build().toVanilla();
        EnvironmentAttributeMap.Builder attributes = EnvironmentAttributeMap.builder().putAll(this.base.getAttributes());
        attributes.set(EnvironmentAttributes.NATURAL_MOB_SPAWNS, this.mobSpawnSettings.build());
        this.attributeModifiers.forEach(consumer -> consumer.accept(attributes));
        BiomeSpecialEffects effects = this.specialEffects.build();
        BiomeGenerationSettings generation = this.generationSettings.build();
        return dev.craftmine.mixin.BiomeAccessor.craftmine$create(climate, attributes.build(), effects, generation);
    }

    public String toString() {
        return "BiomeBuilder{\nclimateSettings=" + String.valueOf(this.climateSettings) + ",\nspecialEffects=" + String.valueOf(this.specialEffects) + ",\nmobSpawnSettings=" + String.valueOf(this.mobSpawnSettings) + ",\ngenerationSettings=" + String.valueOf(this.generationSettings) + ",\n}";
    }
}
