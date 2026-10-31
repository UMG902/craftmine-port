package dev.craftmine.unlock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.craftmine.duck.CraftmineDuckBridge;
import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.registry.CraftmineRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.advancements.predicates.entity.EntitySubPredicate;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import dev.craftmine.unlock.PlayerUnlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Craftmine "unlocks" entity sub-predicate, registered as
 * minecraft:unlocks in the entity_sub_predicate_type registry
 * (CraftmineEntityPredicates).
 *
 * 26.3 adaptation of the fork's type_specific/player.unlocks payload: 26.3
 * removed the type_specific wrapper (EntityPredicate is now a map keyed by
 * registry ids), and the player_unlock registry only fills AFTER the datapack
 * load (its entries build ItemStacks, impossible before 26.3 binds item
 * components post-datapack-load) while advancement JSONs parse DURING it -
 * so unlock ids are kept as identifiers and resolved lazily at test time.
 */
public record UnlockPredicate(List<Identifier> unlocked, List<Identifier> locked)
    implements EntitySubPredicate, Predicate<ServerPlayer> {

    public static final Codec<UnlockPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Identifier.CODEC.listOf().optionalFieldOf("unlocked", List.of())
                    .forGetter(UnlockPredicate::unlocked),
                Identifier.CODEC.listOf().optionalFieldOf("locked", List.of())
                    .forGetter(UnlockPredicate::locked))
            .apply(instance, UnlockPredicate::new));

    @Override
    public boolean matches(Entity entity, ServerLevel serverLevel, Vec3 vec3) {
        return entity instanceof ServerPlayer serverPlayer && test(serverPlayer);
    }

    @Override
    public boolean test(ServerPlayer serverPlayer) {
        CraftminePlayerDuck duck = CraftmineDuckBridge.player(serverPlayer);
        for (Identifier identifier : this.unlocked) {
            Holder<PlayerUnlock> holder = holder(identifier);
            if (holder == null || !duck.isActive(holder)) {
                return false;
            }
        }
        for (Identifier identifier : this.locked) {
            Holder<PlayerUnlock> holder = holder(identifier);
            if (holder != null && duck.isActive(holder)) {
                return false;
            }
        }
        return true;
    }

    private static Holder<PlayerUnlock> holder(Identifier identifier) {
        return CraftmineRegistries.PLAYER_UNLOCK.getOptional(identifier)
            .map(CraftmineRegistries.PLAYER_UNLOCK::wrapAsHolder)
            .orElse(null);
    }

    public static class Builder {
        private final List<Identifier> unlocked = new ArrayList<>();
        private final List<Identifier> locked = new ArrayList<>();

        public static Builder unlocks() {
            return new Builder();
        }

        public Builder isUnlocked(Holder<PlayerUnlock>... holderArray) {
            for (Holder<PlayerUnlock> holder : holderArray) {
                Identifier id = CraftmineRegistries.PLAYER_UNLOCK.getKey(holder.value());
                if (id != null) {
                    this.unlocked.add(id);
                }
            }
            return this;
        }

        public Builder isLocked(Holder<PlayerUnlock> holder) {
            Identifier id = CraftmineRegistries.PLAYER_UNLOCK.getKey(holder.value());
            if (id != null) {
                this.locked.add(id);
            }
            return this;
        }

        public UnlockPredicate build() {
            return new UnlockPredicate(List.copyOf(this.unlocked), List.copyOf(this.locked));
        }
    }
}
