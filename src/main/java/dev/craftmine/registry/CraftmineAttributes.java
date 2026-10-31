/*
 * Craftmine-added attributes (ported from the 25w14craftmine Attributes diff).
 * The fork added these constants directly to vanilla Attributes; here they are
 * registered via the vanilla attribute registry at mod init instead.
 */
package dev.craftmine.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public final class CraftmineAttributes {
    private CraftmineAttributes() {
    }

    // Ported: CraftmineAttributes.EXPERIENCE_GAIN_MODIFIER (25w14craftmine Attributes.java:16)
    public static final Holder<Attribute> EXPERIENCE_GAIN_MODIFIER = register("experience_gain_modifier",
        new RangedAttribute("attribute.name.experience_gain_modifier", 1.0, 0.0, 2048.0));

    // Ported: CraftmineAttributes.MAX_JUMPS (25w14craftmine Attributes.java:48)
    public static final Holder<Attribute> MAX_JUMPS = register("max_jumps",
        new RangedAttribute("attribute.name.max_jumps", 1.0, 0.0, 100.0).setSyncable(true));

    // Ported: CraftmineAttributes.PICKUP_AREA_SIZE (25w14craftmine Attributes.java)
    public static final Holder<Attribute> PICKUP_AREA_SIZE = register("player.pickup_area_size",
        new RangedAttribute("attribute.name.pickup_area_size", 1.0, 0.0, 64.0).setSyncable(true));

    private static Holder<Attribute> register(String name, Attribute attribute) {
        return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, Identifier.withDefaultNamespace(name), attribute);
    }

    public static void bootstrap() {
        // static init registers; touch the fields to force class init
    }
}
