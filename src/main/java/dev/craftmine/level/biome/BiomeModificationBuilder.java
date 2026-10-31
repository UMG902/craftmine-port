package dev.craftmine.level.biome;

import java.util.ArrayList;
import dev.craftmine.level.biome.ClimateSettings;
import dev.craftmine.level.biome.BiomeBuilder;
import dev.craftmine.level.biome.CraftmineExitType;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.Biome;

public class BiomeModificationBuilder {
    private final List<Predicate<Holder.Reference<Biome>>> conditions = new ArrayList<Predicate<Holder.Reference<Biome>>>();
    private final List<Consumer<BiomeBuilder>> modifiers = new ArrayList<Consumer<BiomeBuilder>>();

    public BiomeModificationBuilder thatMatch(Predicate<Holder.Reference<Biome>> predicate) {
        this.conditions.add(predicate);
        return this;
    }

    public BiomeModificationBuilder modifyClimate(Consumer<ClimateSettings.Builder> consumer) {
        this.modifiers.add(biomeBuilder -> consumer.accept(biomeBuilder.climateSettings()));
        return this;
    }

    public BiomeModificationBuilder modifySpecialEffects(Consumer<BiomeSpecialEffects.Builder> consumer) {
        this.modifiers.add(biomeBuilder -> consumer.accept(biomeBuilder.specialEffects()));
        return this;
    }

    public BiomeModificationBuilder modifyExitType(CraftmineExitType craftmineExitType) {
        // Task 42: carry the exit type on the per-mine BiomeBuilder (fork kept it
        // on the biome's special effects); the global map only ever receives
        // MODIFIED per-mine biome ids (WorldGenBuilder propagation), so mines with
        // different exit effects stay independent.
        this.modifiers.add(biomeBuilder -> biomeBuilder.exitType(craftmineExitType));
        return this;
    }

    public BiomeModificationBuilder modifyAttributes(java.util.function.Consumer<net.minecraft.world.attribute.EnvironmentAttributeMap.Builder> consumer) {
        this.modifiers.add(biomeBuilder -> biomeBuilder.modifyAttributes(consumer));
        return this;
    }

    public BiomeModificationBuilder modifyMobSpawns(Consumer<MobSpawnSettings.Builder> consumer) {
        this.modifiers.add(biomeBuilder -> consumer.accept(biomeBuilder.mobSpawnSettings()));
        return this;
    }

    public BiomeModificationBuilder modifyGenerationSettings(Consumer<BiomeGenerationSettings.PlainBuilder> consumer) {
        this.modifiers.add(biomeBuilder -> consumer.accept(biomeBuilder.generationSettings()));
        return this;
    }

    public void apply(BiomeBuilder biomeBuilder) {
        this.modifiers.forEach(consumer -> consumer.accept(biomeBuilder));
    }
}

