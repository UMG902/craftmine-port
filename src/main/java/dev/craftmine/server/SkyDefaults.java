/*
 * Default sky per level, matching the snapshot's own dimension_type JSONs
 * (25w14craftmine shipped sky_type in the effects block: overworld = code
 * rain, generated mine levels = overworld, the_end = end).  26.3 dropped the
 * effects field, so the defaults are mirrored here in code; the Dimension
 * Control override (SkyData) wins over these.
 */
package dev.craftmine.server;

import net.minecraft.world.level.Level;
import dev.craftmine.level.DimensionSpecialEffects;
import net.minecraft.world.level.dimension.DimensionType;

public final class SkyDefaults {
    private SkyDefaults() {
    }

    public static DimensionSpecialEffects.Sky forLevel(Level level) {
        DimensionType.Skybox box = level.dimensionType().skybox();
        if (box == DimensionType.Skybox.END) {
            return DimensionSpecialEffects.EndSky.INSTANCE;
        }
        if (box == DimensionType.Skybox.NONE) {
            return null;
        }
        // OVERWORLD box: the base overworld (hub + mines hub world) defaults to
        // the snapshot's code-rain sky; generated mine levels to the plain sky.
        return level.dimension() == Level.OVERWORLD
            ? DimensionSpecialEffects.CodeSky.INSTANCE
            : DimensionSpecialEffects.OverworldSky.INSTANCE;
    }
}
