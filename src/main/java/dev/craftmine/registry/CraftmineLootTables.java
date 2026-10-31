package dev.craftmine.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Loot table keys used by the craftmine fork that are not vanilla 26.3
 * BuiltInLootTables constants. JSONs ship with the craftmine datapack.
 */
public final class CraftmineLootTables {
    private CraftmineLootTables() {
    }

    public static final ResourceKey<LootTable> ROOM_REWARD = register("gameplay/room_rewards");
    public static final ResourceKey<LootTable> CHICKEN_LAY = register("gameplay/chicken_lay");

    private static ResourceKey<LootTable> register(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace(name));
    }
}
