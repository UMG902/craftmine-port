/*
 * Resource keys for craftmine-added worldgen features.
 * The 26.3 FEATURE registry (worldgen/feature) holds these; the JSONs are shipped
 * in the mod's data folder (migrated from the snapshot's configured_feature folder).
 */
package dev.craftmine.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.Feature;

public final class CraftmineFeatureKeys {
    private CraftmineFeatureKeys() {
    }

    public static final ResourceKey<Feature> MINE_START = createKey("mine_start");
    public static final ResourceKey<Feature> WARDEN_ARENA = createKey("warden_arena");
    public static final ResourceKey<Feature> DIRTY_ICE_BALL = createKey("dirty_ice_ball");
    public static final ResourceKey<Feature> DIRTY_ICE_BALL_FOX = createKey("dirty_ice_ball_fox");
    public static final ResourceKey<Feature> DIRTY_ICE_BALL_GOLEMS = createKey("dirty_ice_ball_golems");
    public static final ResourceKey<Feature> SPACE_IGLOO = createKey("space_igloo");

    private static ResourceKey<Feature> createKey(String name) {
        return ResourceKey.create(Registries.FEATURE, Identifier.withDefaultNamespace(name));
    }
}
