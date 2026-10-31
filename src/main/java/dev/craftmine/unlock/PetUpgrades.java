package dev.craftmine.unlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

/**
 * Global registry of pets granted through unlocks and the upgrades attached to them.
 *
 * <p>Unlock builders ({@link PlayerUnlock.Builder#givesPetInWorld},
 * {@link PlayerUnlock.Builder#upgradePet}) populate this table on activation/relock;
 * {@link #getPet} spawns the pet next to the player when they enter a mine, applying
 * every upgrade recorded for that species. All state lives here rather than on the
 * player so pets survive across mine sessions, mirroring the fork's behaviour.</p>
 */
public final class PetUpgrades {
    private static final Map<EntityType<?>, List<Consumer<TamableAnimal>>> PETS = new HashMap<>();

    private PetUpgrades() {}

    /** Registers a pet species as available in the world (no upgrades yet). */
    public static void addPet(EntityType<?> entityType) {
        PETS.put(entityType, List.of());
    }

    /** Unoriginal relock support: forgets the pet (and its upgrades) again. */
    public static void removePet(EntityType<?> entityType) {
        PETS.remove(entityType);
    }

    /** Unoriginal relock support: removes one previously added upgrade (same consumer instance). */
    public static void removeUpgrade(EntityType<?> entityType, Consumer<TamableAnimal> upgrade) {
        List<Consumer<TamableAnimal>> upgrades = PETS.get(entityType);
        if (upgrades == null) {
            return;
        }
        List<Consumer<TamableAnimal>> remaining = new ArrayList<>(upgrades);
        remaining.remove(upgrade);
        PETS.put(entityType, remaining);
    }

    public static void addUpgrade(EntityType<?> entityType, Consumer<TamableAnimal> upgrade) {
        List<Consumer<TamableAnimal>> upgrades = new ArrayList<>(PETS.getOrDefault(entityType, List.of()));
        upgrades.add(upgrade);
        PETS.put(entityType, upgrades);
    }

    /** Spawns the pet of the given species beside the player (tamed, invulnerable) and applies its upgrades. */
    public static void getPet(EntityType<?> entityType, ServerPlayer player) {
        // Every registered pet species extends TamableAnimal; the wildcard EntityType can only promise Entity.
        TamableAnimal pet = (TamableAnimal)entityType.spawn(player.level(), findAirAbove(player), EntitySpawnReason.EVENT);
        if (pet == null) {
            return;
        }
        pet.setTame(true, false);
        pet.setPermanentlyInvulnerable(true);
        pet.setOwner(player);
        List<Consumer<TamableAnimal>> upgrades = PETS.get(entityType);
        if (upgrades != null) {
            upgrades.forEach(upgrade -> upgrade.accept(pet));
        }
    }

    /** Applies all recorded upgrades of the species to the nearest living pet of that type around the player. */
    public static void upgradePet(EntityType<?> entityType, Class<? extends TamableAnimal> petClass, ServerPlayer player) {
        List<Consumer<TamableAnimal>> upgrades = PETS.get(entityType);
        if (upgrades == null) {
            return;
        }
        ServerLevel level = player.level();
        TamableAnimal pet = level.getNearestEntity(petClass, TargetingConditions.forNonCombat(), player,
            player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(10.0, 10.0, 10.0));
        if (pet == null) {
            return;
        }
        upgrades.forEach(upgrade -> upgrade.accept(pet));
    }

    /** The first collision-free position one block west of the player, scanning upward - pets must not spawn inside blocks. */
    private static BlockPos findAirAbove(ServerPlayer player) {
        BlockPos.MutableBlockPos pos = player.blockPosition().mutable().move(Direction.WEST);
        ServerLevel level = player.level();
        while (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            pos.move(Direction.UP);
        }
        return pos.immutable();
    }
}
