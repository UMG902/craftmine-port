/*
 * Exposes EnderDragonFight internals used by the craftmine dragon battle event.
 */
package dev.craftmine.mixin;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccessor {
    @Accessor("dragonUUID")
    UUID craftmine$getDragonUUID();

    @Accessor("exitPortalLocation")
    BlockPos craftmine$getExitPortalLocation();

    @Accessor("exitPortalLocation")
    void craftmine$setExitPortalLocation(BlockPos pos);
}
