package dev.craftmine.mixin;

import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes DerivedLevelData's wrapped ServerLevelData so craftmine world state
 * (effects, special mines, mine crafter level, level count) can be reached from
 * mine dimensions, whose level data is a vanilla DerivedLevelData wrapper.
 */
@Mixin(DerivedLevelData.class)
public interface DerivedLevelDataAccessor {
    @Accessor("wrapped")
    ServerLevelData craftmine$getWrapped();
}
