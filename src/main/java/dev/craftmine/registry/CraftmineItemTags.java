package dev.craftmine.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Craftmine-specific item tags. The JSON definitions ship with the bundled
 * craftmine datapack; these constants give code a typed handle on them.
 */
public class CraftmineItemTags {
    public static final TagKey<Item> ORE = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("ore"));
    public static final TagKey<Item> CARRY_OVER = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("carry_over"));
    public static final TagKey<Item> INGOT = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("ingot"));
}
