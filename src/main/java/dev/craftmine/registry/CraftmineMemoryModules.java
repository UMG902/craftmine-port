/*
 * Craftmine-added memory module types (ported from the 25w14craftmine
 * MemoryModuleType diff). The fork added ACTING_STAGE for the warden
 * boss-fight "acting" sequence.
 */
package dev.craftmine.registry;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public final class CraftmineMemoryModules {
    private CraftmineMemoryModules() {
    }

    // Ported: CraftmineMemoryModules.ACTING_STAGE (25w14craftmine MemoryModuleType.java:122)
    public static final MemoryModuleType<Integer> ACTING_STAGE = register("acting_stage", Codec.INT);

    private static <U> MemoryModuleType<U> register(String name, Codec<U> codec) {
        MemoryModuleType<U> type = new MemoryModuleType<U>(Optional.of(codec));
        return Registry.register(BuiltInRegistries.MEMORY_MODULE_TYPE, Identifier.withDefaultNamespace(name), type);
    }

    public static void bootstrap() {
        // static init registers; touch the field to force class init
    }
}
