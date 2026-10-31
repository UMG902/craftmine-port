/*
 * FT-8 helper: builds the fork's "minecraft:devour" damage source (damage
 * type ships in data/minecraft/damage_type/devour.json; 26.3 dropped the
 * DamageSources.devour() accessor the fork used for WEDNESDAY_FROGS tongue
 * kills).
 */
package dev.craftmine.server;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

public final class CraftmineDevourDamage {
    private static final ResourceKey<DamageType> DEVOUR =
        ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.withDefaultNamespace("devour"));

    private CraftmineDevourDamage() {
    }

    public static DamageSource devour(ServerLevel serverLevel, Entity frog) {
        Holder<DamageType> holder = serverLevel.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DEVOUR);
        return new DamageSource(holder, frog);
    }
}
