/*
 * Small helpers replacing craftmine additions to vanilla helper classes.
 */
package dev.craftmine.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.core.registries.Registries;

public final class CraftmineRegistryHelpers {
    private CraftmineRegistryHelpers() {
    }

    /** Ported from craftmine's Registries.levelToLevelStem. */
    public static ResourceKey<LevelStem> levelToLevelStem(ResourceKey<Level> levelKey) {
        return ResourceKey.create(Registries.LEVEL_STEM, levelKey.identifier());
    }
}
