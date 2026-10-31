/*
 * Exposes ServerLevel.dragonFight for the craftmine dragon battle event.
 */
package dev.craftmine.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import org.jetbrains.annotations.Nullable;

@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {
    @Accessor("dragonFight")
    @Nullable
    EnderDragonFight craftmine$getDragonFight();

    @Accessor("dragonFight")
    void craftmine$setDragonFight(@Nullable EnderDragonFight fight);
}
