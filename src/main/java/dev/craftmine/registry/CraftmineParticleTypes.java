/*
 * Craftmine-added particle types (the fork added these to ParticleTypes).
 */
package dev.craftmine.registry;

import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class CraftmineParticleTypes {
    private CraftmineParticleTypes() {
    }

    private static final List<SimpleParticleType> ALL = new ArrayList<>();

    public static final SimpleParticleType MINE_TRAVEL = register("mine_travel");

    /**
     * Forces the static registration above to run inside the vanilla bootstrap
     * window (see CraftmineBootstrap.bootstrapAll). The type must be in
     * BuiltInRegistries.PARTICLE_TYPE BEFORE BuiltInRegistries.freeze() -
     * if the class is first touched later (e.g. by the client particle
     * provider mixin during Minecraft.<init>), registration throws
     * "Registry is already frozen". Idempotent: <clinit> runs once.
     */
    public static void bootstrap() {
    }

    private static SimpleParticleType register(String name) {
        SimpleParticleType type = new CraftmineSimpleParticle();
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.withDefaultNamespace(name), type);
        ALL.add(type);
        return type;
    }

    private static final class CraftmineSimpleParticle extends SimpleParticleType {
        private CraftmineSimpleParticle() {
            super(false);
        }
    }
}
