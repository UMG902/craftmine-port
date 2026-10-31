/*
 * FT-8 / warden boss intro: the fork's ACTING activity registration. 26.3
 * has no Activity.ACTING (the fork added it), so we register our own instance
 * through the same private factory vanilla uses.
 */
package dev.craftmine.registry;

import net.minecraft.world.entity.schedule.Activity;

public final class CraftmineActivities {
    /** Fork Activity.ACTING (25w14craftmine Activity.java) - "acting". */
    public static final Activity ACTING = dev.craftmine.mixin.ActivityAccessor.craftmine$register("acting");

    private CraftmineActivities() {
    }

    public static void boop() {
        // static init registers; touch the field to force class init
    }
}
