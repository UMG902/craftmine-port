package dev.craftmine.item;

import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.unlock.PlayerUnlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;

/**
 * The three sorcerer wands from the Minecrafter's unlock tree, consolidated
 * into one file. All of them fire a projectile on use; they differ only in
 * what they shoot and whether an unlock gates a stronger variant.
 *
 * <ul>
 *   <li>{@link Wind} - a wind charge at the vanilla wind-charge power.</li>
 *   <li>{@link Teleportation} - an ender pearl thrown at fixed power 10.</li>
 *   <li>{@link Fire} - a large fireball; with both SORCERER_SUPREME and
 *       DRAGON_FIRE unlocked it upgrades to a dragon fireball.</li>
 * </ul>
 */
public final class WandItems {

	private WandItems() {
	}

	/** Shared shoot logic: spawns {@code projectile} from the player's view rotation. */
	private static InteractionResult shoot(Level level, Player player, ItemStack ammo, float power,
			ProjectileFactory factory) {
		if (level instanceof ServerLevel serverLevel) {
			Projectile.spawnProjectileFromRotation(
				(spawnLevel, owner, stack) -> factory.create(serverLevel, owner, stack),
				serverLevel, ammo, player, 0.0f, power, 0.0f);
		}
		return InteractionResult.SUCCESS;
	}

	@FunctionalInterface
	private interface ProjectileFactory {
		Projectile create(ServerLevel level, LivingEntity owner, ItemStack ammo);
	}

	/** The wind wand: throws wind charges. */
	public static class Wind extends Item {
		public Wind(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			return shoot(level, player, Items.WIND_CHARGE.getDefaultInstance().copy(),
				WindChargeItem.PROJECTILE_SHOOT_POWER,
				(spawnLevel, owner, stack) -> new WindCharge(player, level,
					player.position().x(), player.getEyePosition().y(), player.position().z()));
		}
	}

	/** The teleportation wand: throws ender pearls at fixed power 10. */
	public static class Teleportation extends Item {
		public Teleportation(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			return shoot(level, player, Items.ENDER_PEARL.getDefaultInstance().copy(), 10.0f,
				(spawnLevel, owner, stack) -> new ThrownEnderpearl(spawnLevel, owner, stack));
		}
	}

	/** The fire wand: fireballs, upgraded to dragon fire by the sorcerer unlocks. */
	public static class Fire extends Item {
		public Fire(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (level instanceof ServerLevel) {
				Vec3 direction = player.getViewVector(1.0f);
				if (player instanceof ServerPlayer serverPlayer) {
					CraftminePlayerDuck duck = (CraftminePlayerDuck) serverPlayer;
					if (duck.isActive(PlayerUnlocks.SORCERER_SUPREME) && duck.isActive(PlayerUnlocks.DRAGON_FIRE)) {
						DragonFireball fireball = new DragonFireball(level, player, direction.normalize());
						fireball.accelerationPower = 1.0;
						level.addFreshEntity(fireball);
					} else {
						LargeFireball fireball = new LargeFireball(level, player, direction.normalize(), 1);
						fireball.accelerationPower = 1.0;
						level.addFreshEntity(fireball);
					}
				}
			}
			return InteractionResult.SUCCESS;
		}
	}
}
