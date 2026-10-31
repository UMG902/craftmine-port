/*
 * FT-8 helper: exposes LivingEntity's protected dropFromLootTable so the
 * HUNTER double-drop (MobMixin) can re-run the entity's loot table exactly
 * like the fork's Animal.dropFromLootTable super-call did.
 */
package dev.craftmine.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(net.minecraft.world.entity.LivingEntity.class)
public interface CraftmineLivingEntityInvoker {
    @Invoker("dropFromLootTable")
    void craftmine$invokeDropFromLootTable(ServerLevel serverLevel, DamageSource damageSource, boolean playerKilled);
}
