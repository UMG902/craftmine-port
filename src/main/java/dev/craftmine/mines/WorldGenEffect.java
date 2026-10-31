package dev.craftmine.mines;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * World-generation component of a {@link WorldEffect}: applied once to the mine's
 * {@link WorldGenBuilder} while the mine is crafted, before any chunk generates.
 */
@FunctionalInterface
public interface WorldGenEffect extends WorldEffectComponent {

    /** Applies this effect's changes to the mine's world generation. */
    void modifyWorld(WorldGenBuilder worldGen);

    /**
     * Contributes a fixed list of biomes to the mine. A record because effect
     * definitions usually just name the biomes they want.
     */
    record AddBiome(List<ResourceKey<Biome>> biomes) implements WorldGenEffect {

        @SafeVarargs
        public AddBiome(ResourceKey<Biome>... biomes) {
            this(List.of(biomes));
        }

        @Override
        public void modifyWorld(WorldGenBuilder worldGen) {
            this.biomes.forEach(worldGen::addBiome);
        }
    }
}
