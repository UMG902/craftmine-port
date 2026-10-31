package dev.craftmine.unlock;

import java.util.Arrays;
import java.util.Set;
import java.util.function.BiFunction;

import com.mojang.datafixers.util.Function4;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;

import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.mines.SpecialMine;
import dev.craftmine.mines.WorldEffect;
import dev.craftmine.registry.CraftmineRegistries;
import dev.craftmine.registry.CraftmineTriggers;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One "this mine world effect / special mine / unlock becomes visible when ..." rule.
 *
 * <p>The interface only declares the hooks it cares about; every hook defaults to
 * {@code false} so a single condition can react to exactly one event type. Static
 * factories build the standard shapes (lambda predicates, entity-type kills, "mine
 * completed with effect X"). The {@code on*} dispatch methods are called from mixins
 * and vanilla hooks; they walk every registered world effect / special mine / player
 * unlock and fire the matching unlock drop when a condition passes.</p>
 */
public interface UnlockCondition {
    default boolean playerDied(ServerLevel level, ServerPlayer player, DamageSource damageSource) {
        return false;
    }

    default boolean playerTookDamage(ServerLevel level, ServerPlayer player, DamageSource damageSource, float amount) {
        return false;
    }

    default boolean playerKilledEntity(ServerLevel level, ServerPlayer player, Entity entity) {
        return false;
    }

    /*
     * Task 80 (breeze_boss special mine, unoriginal features): fired when a
     * player lands a vanilla MACE_SMASH on a mob (see
     * CraftmineBreezeBossMixins$LivingEntityMixin). "Unlocked by smashing a
     * mob in a mine" - inherently requires a mace.
     */
    default boolean playerSmashedEntity(ServerLevel level, ServerPlayer player, Entity entity, float amount) {
        return false;
    }

    default boolean playerAdvancement(ServerLevel level, AdvancementHolder advancement) {
        return false;
    }

    default boolean blockBreak(ServerLevel level, BlockState blockState, BlockPos blockPos) {
        return false;
    }

    default boolean blockDrops(ServerLevel level, BlockState blockState) {
        return false;
    }

    default boolean blockPlace(ServerLevel level, BlockState blockState, BlockPos blockPos) {
        return false;
    }

    default boolean craftedItem(ServerLevel level, ServerPlayer player, ItemStack itemStack) {
        return false;
    }

    default boolean obtainedItem(ServerLevel level, ServerPlayer player, ItemStack itemStack) {
        return false;
    }

    default boolean usedItem(ServerLevel level, ServerPlayer player, ItemStack itemStack) {
        return false;
    }

    default boolean fedAnimal(ServerLevel level, ServerPlayer player, Animal animal, ItemStack itemStack) {
        return false;
    }

    default boolean unlocked(ServerLevel level, ServerPlayer player, PlayerUnlock playerUnlock) {
        return false;
    }

    default boolean unlocked(ServerLevel level, ServerPlayer player, WorldEffect worldEffect) {
        return false;
    }

    /** @param won false when the mine was exited without completion. */
    default boolean mapCompleted(ServerLevel level, ServerPlayer player, Set<WorldEffect> worldEffects, boolean won) {
        return false;
    }

    default boolean specialCompleted(ServerLevel level, ServerPlayer player, SpecialMine specialMine, boolean won) {
        return false;
    }

    // ---- standard condition factories ----

    public static UnlockCondition playerTookDamage(Function4<ServerLevel, ServerPlayer, DamageSource, Float, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean playerTookDamage(ServerLevel level, ServerPlayer player, DamageSource damageSource, float amount) {
                return predicate.apply(level, player, damageSource, amount);
            }
        };
    }

    public static UnlockCondition playerDied(TriFunction<ServerLevel, ServerPlayer, DamageSource, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean playerDied(ServerLevel level, ServerPlayer player, DamageSource damageSource) {
                return predicate.apply(level, player, damageSource);
            }
        };
    }

    public static UnlockCondition playerKilledEntity(EntityType<? extends Entity> entityType) {
        return playerKilledEntity(1.0f, entityType);
    }

    @SafeVarargs
    public static UnlockCondition playerKilledEntity(float chance, EntityType<? extends Entity>... entityTypes) {
        return new UnlockCondition() {
            @Override
            public boolean playerKilledEntity(ServerLevel level, ServerPlayer player, Entity entity) {
                return Arrays.stream(entityTypes).anyMatch(type -> type == entity.getType()) && level.random.nextDouble() < chance;
            }
        };
    }

    public static UnlockCondition playerKilledEntity(TriFunction<ServerLevel, ServerPlayer, Entity, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean playerKilledEntity(ServerLevel level, ServerPlayer player, Entity entity) {
                return predicate.apply(level, player, entity);
            }
        };
    }

    public static UnlockCondition playerAdvancement(BiFunction<ServerLevel, AdvancementHolder, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean playerAdvancement(ServerLevel level, AdvancementHolder advancement) {
                return predicate.apply(level, advancement);
            }
        };
    }

    public static UnlockCondition blockBreak(BiFunction<ServerLevel, BlockState, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean blockBreak(ServerLevel level, BlockState blockState, BlockPos blockPos) {
                return predicate.apply(level, blockState);
            }
        };
    }

    public static UnlockCondition blockBreak(TriFunction<ServerLevel, BlockState, BlockPos, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean blockBreak(ServerLevel level, BlockState blockState, BlockPos blockPos) {
                return predicate.apply(level, blockState, blockPos);
            }
        };
    }

    public static UnlockCondition blockDrops(BiFunction<ServerLevel, BlockState, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean blockDrops(ServerLevel level, BlockState blockState) {
                return predicate.apply(level, blockState);
            }
        };
    }

    public static UnlockCondition blockPlace(BiFunction<ServerLevel, BlockState, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean blockPlace(ServerLevel level, BlockState blockState, BlockPos blockPos) {
                return predicate.apply(level, blockState);
            }
        };
    }

    public static UnlockCondition blockPlace(TriFunction<ServerLevel, BlockState, BlockPos, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean blockPlace(ServerLevel level, BlockState blockState, BlockPos blockPos) {
                return predicate.apply(level, blockState, blockPos);
            }
        };
    }

    public static UnlockCondition craftedItem(TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean craftedItem(ServerLevel level, ServerPlayer player, ItemStack itemStack) {
                return predicate.apply(level, player, itemStack);
            }
        };
    }

    public static UnlockCondition usedItem(TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean usedItem(ServerLevel level, ServerPlayer player, ItemStack itemStack) {
                return predicate.apply(level, player, itemStack);
            }
        };
    }

    public static UnlockCondition obtainedItem(TriFunction<ServerLevel, ServerPlayer, ItemStack, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean obtainedItem(ServerLevel level, ServerPlayer player, ItemStack itemStack) {
                return predicate.apply(level, player, itemStack);
            }
        };
    }

    public static UnlockCondition fedAnimal(Function4<ServerLevel, ServerPlayer, Animal, ItemStack, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean fedAnimal(ServerLevel level, ServerPlayer player, Animal animal, ItemStack itemStack) {
                return predicate.apply(level, player, animal, itemStack);
            }
        };
    }

    /** Fires when the unlock with the given holder's key is bought (key compare keeps the condition instance-stateless). */
    public static UnlockCondition unlocked(Holder<PlayerUnlock> holder) {
        return new UnlockCondition() {
            @Override
            public boolean unlocked(ServerLevel level, ServerPlayer player, PlayerUnlock playerUnlock) {
                return playerUnlock.key().equals(holder.value().key());
            }
        };
    }

    /** Fires when the world effect with the same key as {@code worldEffect} is unlocked. */
    public static UnlockCondition unlocked(WorldEffect worldEffect) {
        return new UnlockCondition() {
            @Override
            public boolean unlocked(ServerLevel level, ServerPlayer player, WorldEffect unlockedEffect) {
                return unlockedEffect.key().equals(worldEffect.key());
            }
        };
    }

    /** @param requireWin when true the mine must actually be completed, not just exited. */
    public static UnlockCondition mineCompletedWith(boolean requireWin, WorldEffect... requiredEffects) {
        return new UnlockCondition() {
            @Override
            public boolean mapCompleted(ServerLevel level, ServerPlayer player, Set<WorldEffect> worldEffects, boolean won) {
                if (requireWin && !won) {
                    return false;
                }
                return worldEffects.containsAll(Arrays.asList(requiredEffects));
            }
        };
    }

    public static UnlockCondition mineWon(BiFunction<ServerLevel, ServerPlayer, Boolean> predicate) {
        return new UnlockCondition() {
            @Override
            public boolean mapCompleted(ServerLevel level, ServerPlayer player, Set<WorldEffect> worldEffects, boolean won) {
                return won && predicate.apply(level, player);
            }
        };
    }

    /** @param requireWin when true the special mine must actually be completed. */
    public static UnlockCondition specialCompleted(boolean requireWin) {
        return new UnlockCondition() {
            @Override
            public boolean specialCompleted(ServerLevel level, ServerPlayer player, SpecialMine specialMine, boolean won) {
                return !requireWin || won;
            }
        };
    }

    /** Fires when {@code specialMine} itself is (optionally won-) completed, regardless of which instance fired the event. */
    public static UnlockCondition specialCompleted(boolean requireWin, SpecialMine specialMine) {
        return new UnlockCondition() {
            @Override
            public boolean specialCompleted(ServerLevel level, ServerPlayer player, SpecialMine completedMine, boolean won) {
                if (requireWin && !won) {
                    return false;
                }
                return specialMine == completedMine;
            }
        };
    }

    // ---- event dispatch (called from mixins / vanilla hooks) ----

    public static void onPlayerTookDamage(Level level, ServerPlayer player, DamageSource damageSource, float amount) {
        UnlockCondition.checkAndUnlockDrop(level, player, player.position(), (serverLevel, condition) -> condition.playerTookDamage(serverLevel, player, damageSource, amount));
    }

    public static void onPlayerDied(Level level, ServerPlayer player, DamageSource damageSource) {
        UnlockCondition.checkAndUnlockDrop(level, player, player.position(), (serverLevel, condition) -> condition.playerDied(serverLevel, player, damageSource));
    }

    public static void onPlayerKilledEntity(Level level, ServerPlayer player, Entity entity) {
        UnlockCondition.checkAndUnlockDrop(level, player, entity.position(), (serverLevel, condition) -> condition.playerKilledEntity(serverLevel, player, entity));
    }

    public static void onPlayerSmashedEntity(Level level, ServerPlayer player, Entity entity, float amount) {
        UnlockCondition.checkAndUnlockDrop(level, player, entity.position(), (serverLevel, condition) -> condition.playerSmashedEntity(serverLevel, player, entity, amount));
    }

    public static void onPlayerAdvancement(Level level, ServerPlayer player, AdvancementHolder advancement) {
        UnlockCondition.checkAndUnlockDrop(level, player, player.position(), (serverLevel, condition) -> condition.playerAdvancement(serverLevel, advancement));
    }

    public static void onBlockBreak(Level level, ServerPlayer player, BlockPos blockPos, BlockState blockState) {
        UnlockCondition.checkAndUnlockDrop(level, player, Vec3.atCenterOf(blockPos), (serverLevel, condition) -> condition.blockBreak(serverLevel, blockState, blockPos));
    }

    public static void onBlockDrops(ServerLevel level, BlockPos blockPos, BlockState blockState) {
        // No player: block drops can fire from non-player destruction (dispensers, explosions).
        UnlockCondition.checkAndUnlockDrop(level, null, Vec3.atCenterOf(blockPos), (serverLevel, condition) -> condition.blockDrops(serverLevel, blockState));
    }

    public static void onBlockPlace(Level level, ServerPlayer player, BlockPos blockPos, BlockState blockState) {
        UnlockCondition.checkAndUnlockDrop(level, player, Vec3.atCenterOf(blockPos), (serverLevel, condition) -> condition.blockPlace(serverLevel, blockState, blockPos));
    }

    public static void onCraftedItem(Level level, ServerPlayer player, ItemStack itemStack) {
        UnlockCondition.checkAndUnlockDrop(level, player, player.position(), (serverLevel, condition) -> condition.craftedItem(serverLevel, player, itemStack));
    }

    public static void onUsedItem(Level level, ServerPlayer player, ItemStack itemStack) {
        UnlockCondition.checkAndUnlockDrop(level, player, player.position(), (serverLevel, condition) -> condition.usedItem(serverLevel, player, itemStack));
    }

    public static void onObtainedItem(Level level, ServerPlayer player, ItemStack itemStack) {
        UnlockCondition.checkAndUnlockDrop(level, player, player.position(), (serverLevel, condition) -> condition.obtainedItem(serverLevel, player, itemStack));
    }

    public static void onFedAnimal(Level level, ServerPlayer player, Animal animal, ItemStack itemStack) {
        UnlockCondition.checkAndUnlockDrop(level, player, player.position(), (serverLevel, condition) -> condition.fedAnimal(serverLevel, player, animal, itemStack));
    }

    public static void onUnlockedPlayerUnlock(Level level, ServerPlayer player, Holder<PlayerUnlock> holder) {
        UnlockCondition.checkAndUnlockDrop(level, player, player.position(), (serverLevel, condition) -> condition.unlocked(serverLevel, player, holder.value()));
    }

    public static void onUnlockedMapEffect(Level level, ServerPlayer player, WorldEffect worldEffect) {
        UnlockCondition.checkAndUnlockDrop(level, player, player.position(), (serverLevel, condition) -> condition.unlocked(serverLevel, player, worldEffect));
    }

    public static void onMapCompleted(Level level, ServerPlayer player, Set<WorldEffect> worldEffects, boolean won) {
        UnlockCondition.checkAndUnlockDrop(level, player, player.position(), (serverLevel, condition) -> condition.mapCompleted(serverLevel, player, worldEffects, won));
    }

    public static void onSpecialMineCompleted(Level level, ServerPlayer player, SpecialMine specialMine, boolean won) {
        UnlockCondition.checkAndUnlockDrop(level, player, player.position(), (serverLevel, condition) -> condition.specialCompleted(serverLevel, player, specialMine, won));
    }

    /**
     * The common dispatch: skip revisits, then walk every world effect / special mine /
     * player unlock whose "made visible by" conditions are candidates, and fire the unlock
     * drop for each whose prerequisites are all unlocked and whose predicate passes.
     *
     * @param player may be null for player-less events (block drops); player-unlock
     *               visibility checks are skipped in that case
     */
    private static void checkAndUnlockDrop(Level level, @Nullable ServerPlayer player, Vec3 dropPos, BiFunction<ServerLevel, UnlockCondition, Boolean> eventMatched) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        CraftmineLevelDuck levelDuck = (CraftmineLevelDuck)serverLevel;
        if (player != null && ((CraftminePlayerDuck)player).isRevisiting()) {
            return;
        }
        // NOTE: state checks stay inside the condition loop on purpose - dropUnlockEffect /
        // unlockSpecialMine can themselves mutate unlock state (re-entrantly, via unlockEffect),
        // and each remaining condition must re-evaluate against the fresh state.
        for (WorldEffect effect : CraftmineRegistries.WORLD_EFFECT) {
            for (UnlockCondition condition : effect.unlockedBy()) {
                if (levelDuck.isEffectUnlocked(effect)) {
                    continue;
                }
                if (!prerequisitesUnlocked(effect.unlockedAfter(), levelDuck::isEffectUnlocked)) {
                    continue;
                }
                if (eventMatched.apply(serverLevel, condition)) {
                    levelDuck.dropUnlockEffect(dropPos, effect, player);
                }
            }
        }
        for (SpecialMine specialMine : CraftmineRegistries.SPECIAL_MINE) {
            for (UnlockCondition condition : specialMine.unlockedBy()) {
                if (levelDuck.isSpecialMineUnlocked(specialMine)) {
                    continue;
                }
                if (!prerequisitesUnlocked(specialMine.unlockedAfter(), levelDuck::isSpecialMineUnlocked)) {
                    continue;
                }
                if (eventMatched.apply(serverLevel, condition)) {
                    levelDuck.unlockSpecialMine(specialMine);
                }
            }
        }
        if (player != null) {
            for (Holder<PlayerUnlock> holder : CraftmineRegistries.PLAYER_UNLOCK.listElements().toList()) {
                for (UnlockCondition condition : holder.value().madeVisibleBy()) {
                    if (eventMatched.apply(serverLevel, condition)) {
                        ((CraftminePlayerDuck)player).makeVisible(holder);
                        CraftmineTriggers.PLAYER_UNLOCK_UNLOCKED.trigger(player);
                    }
                }
            }
        }
    }

    /** A candidate unlocks only once every prerequisite (in its "unlocked after" list) is unlocked already. */
    private static <T> boolean prerequisitesUnlocked(Iterable<T> prerequisites, java.util.function.Predicate<T> isUnlocked) {
        for (T prerequisite : prerequisites) {
            if (!isUnlocked.test(prerequisite)) {
                return false;
            }
        }
        return true;
    }
}
