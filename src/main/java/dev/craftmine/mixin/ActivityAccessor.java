/*
 * Accessor: exposes Activity's private register factory so the port can add
 * the fork's ACTING activity (same static registry vanilla bootstraps from).
 */
package dev.craftmine.mixin;

import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Activity.class)
public interface ActivityAccessor {
    @Invoker("register")
    static Activity craftmine$register(String name) {
        throw new AssertionError("mixin invoker");
    }
}
