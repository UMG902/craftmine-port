package dev.craftmine.registry;

import com.mojang.serialization.Codec;
import dev.craftmine.duck.CraftmineDuckBridge;
import java.util.List;
import net.minecraft.advancements.predicates.entity.EntitySubPredicate;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import dev.craftmine.mines.SpecialMine;
import dev.craftmine.mines.WorldEffect;
import net.minecraft.world.phys.Vec3;

/**
 * Craftmine entity sub-predicate codecs, registered into the 26.3
 * entity_sub_predicate_type registry during vanilla bootstrap.
 *
 * 26.3 turned EntityPredicate from a fixed record into a registry-keyed map
 * of sub-predicate codecs; the fork's custom LocationPredicate fields
 * (special_mine, world effects) and the type_specific/player.unlocks payload
 * become first-class sub-predicate ids here, mirroring how vanilla 26.3
 * registered type_specific/player, type_specific/sheep etc.
 *
 * All three resolve registry content lazily at test time: the craftmine
 * registries (player_unlock / special_mine / world_effect) fill only after
 * the datapack load (their entries build ItemStacks), while advancement JSONs
 * parse during it.
 */
public final class CraftmineEntityPredicates {

    private CraftmineEntityPredicates() {}

    /** "minecraft:special_mine": "<id>" - the checked level runs this mine. */
    public record SpecialMinePredicate(Identifier mine) implements EntitySubPredicate {
        public static final Codec<SpecialMinePredicate> CODEC =
            Identifier.CODEC.xmap(SpecialMinePredicate::new, SpecialMinePredicate::mine);

        @Override
        public boolean matches(Entity entity, ServerLevel serverLevel, Vec3 vec3) {
            return CraftmineDuckBridge.level(serverLevel).specialMine()
                .map(mine -> this.mine.equals(CraftmineRegistries.SPECIAL_MINE.getKey(mine)))
                .orElse(false);
        }
    }

    /**
     * "minecraft:world_effects": ["<id>", ...] - ALL listed world effects are
     * active on the checked level (mirrors the fork's LocationPredicate loop).
     */
    public record WorldEffectsPredicate(List<Identifier> effects) implements EntitySubPredicate {
        public static final Codec<WorldEffectsPredicate> CODEC =
            Identifier.CODEC.listOf().xmap(WorldEffectsPredicate::new, WorldEffectsPredicate::effects);

        @Override
        public boolean matches(Entity entity, ServerLevel serverLevel, Vec3 vec3) {
            for (Identifier identifier : this.effects) {
                WorldEffect worldEffect = CraftmineRegistries.WORLD_EFFECT.getValue(identifier);
                if (worldEffect == null || !CraftmineDuckBridge.level(serverLevel).isActive(worldEffect)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static void bootstrap() {
        register("special_mine", SpecialMinePredicate.CODEC);
        register("world_effects", WorldEffectsPredicate.CODEC);
        register("unlocks", dev.craftmine.unlock.UnlockPredicate.CODEC);
    }

    private static void register(String name, Codec<? extends EntitySubPredicate> codec) {
        Registry.register(BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE,
            Identifier.withDefaultNamespace(name), codec);
    }
}
