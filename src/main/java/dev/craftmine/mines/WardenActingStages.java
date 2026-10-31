package dev.craftmine.mines;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.duck.CraftmineWardenDuck;
import dev.craftmine.registry.CraftmineSoundEvents;
import dev.craftmine.registry.CraftmineMemoryModules;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.commands.arguments.EntityAnchorArgument;

/**
 * The warden arena boss-intro show (warden boss mine finale): a chain of six
 * "acting" stage behaviors, ported 1:1 from the snapshot's
 * {@code Stages} classes and consolidated here.
 *
 * <p>The show is driven by the {@code CraftmineMemoryModules.ACTING_STAGE}
 * memory; each stage runs while that memory holds its own index, then either
 * advances the memory (stage cleared) or aborts the show. External code (the
 * cutscene controller) advances the stage out of band during the intro; if a
 * stage simply times out instead, the show is cancelled.
 *
 * <p>Port notes for 26.3: the fork's {@code MemoryModuleType.ACTING_STAGE}
 * became {@link CraftmineMemoryModules#ACTING_STAGE}, arena position reads go
 * through {@link CraftmineLevelDuck#craftmine$wardenArenaPos()}, the crowd
 * sounds are {@link CraftmineSoundEvents#VILLAGER_CROWD_START}/
 * {@link CraftmineSoundEvents#VILLAGER_CROWD_CHEER}, and the CFR synthetic
 * LivingEntity bridge overrides of the decompile were dropped (26.3's
 * {@code Behavior} is properly generic).
 */
public final class WardenActingStages {

	private WardenActingStages() {
	}

	/** The full behavior list for the ACTING activity, in stage order. */
	public static List<Behavior<Warden>> actingShow() {
		return List.of(
			new PoseStage(-1, 6000, null, Pose.SLEEPING, Flavor.ABORT_TO_ATTACK),
			new PoseStage(0, 1200, new net.minecraft.world.phys.Vec3(-3.0, 0.0, 7.0), Pose.EMERGING, Flavor.CROWD_START),
			new PoseStage(1, 1200, new net.minecraft.world.phys.Vec3(0.0, 0.0, -14.0), Pose.STANDING, Flavor.PLAIN),
			new PoseStage(2, 1200, new net.minecraft.world.phys.Vec3(0.0, 0.0, 7.0), Pose.SLEEPING, Flavor.WORM),
			new PoseStage(3, 1200, new net.minecraft.world.phys.Vec3(3.0, 0.0, 0.0), Pose.ROARING, Flavor.PLAIN),
			new FinaleStage(60, Pose.DIGGING));
	}

	/** Extra behavior each pose stage layers onto the plain advance-or-abort flow. */
	private enum Flavor {
		/** Intro nap: on timeout the warden gives up and attacks the nearest player. */
		ABORT_TO_ATTACK,
		/** Emerging from the arena floor: crowd sound on start, "too tired" toast on abort. */
		CROWD_START,
		/** Nothing special. */
		PLAIN,
		/** The worm dance: one-shot chat toast plus wake-up-from-sleeping handling. */
		WORM
	}

	/** Shared plumbing: stage index bookkeeping against the ACTING_STAGE memory. */
	abstract static class ActingStage<E extends Warden> extends Behavior<E> {

		ActingStage(int duration) {
			super(Map.of(), duration);
		}

		protected static int stage(Warden warden) {
			return warden.getBrain().getMemory(CraftmineMemoryModules.ACTING_STAGE).orElse(Integer.MIN_VALUE);
		}

		/** True while the warden is idle (no attack target) and parked on the given stage. */
		protected static boolean waiting(Warden warden, int stageIndex) {
			return !warden.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
				&& warden.getBrain().getMemory(CraftmineMemoryModules.ACTING_STAGE).isPresent()
				&& stage(warden) == stageIndex;
		}
	}

	/**
	 * A generic timed stage: pose + look direction while running, then either
	 * walk to {@code pathfindingOffset} and advance to the next stage, or abort.
	 * Covers intro (stage -1) through the roar (stage 3).
	 */
	static final class PoseStage<E extends Warden> extends ActingStage<E> {

		private final int stageIndex;
		@Nullable
		private final net.minecraft.world.phys.Vec3 pathfindingOffset;
		private final Pose pose;
		private final Flavor flavor;
		private boolean wormMessageSent;

		PoseStage(int stageIndex, int duration, @Nullable net.minecraft.world.phys.Vec3 pathfindingOffset,
				Pose pose, Flavor flavor) {
			super(duration);
			this.stageIndex = stageIndex;
			this.pathfindingOffset = pathfindingOffset;
			this.pose = pose;
			this.flavor = flavor;
		}

		@Override
		protected boolean checkExtraStartConditions(ServerLevel level, E warden) {
			return waiting(warden, this.stageIndex);
		}

		@Override
		protected boolean canStillUse(ServerLevel level, E warden, long time) {
			if (this.flavor == Flavor.WORM && !wormMessageSent && !level.players().isEmpty()) {
				this.wormMessageSent = true;
				level.players().get(0).sendSystemMessage(
					Component.literal("IT'S DOING THE WORM! THE WORM!"), true);
			}
			return waiting(warden, this.stageIndex);
		}

		@Override
		protected void start(ServerLevel level, E warden, long time) {
			warden.setPose(this.pose);
			warden.lookAt(EntityAnchorArgument.Anchor.EYES, warden.position().add(-1.0, 0.0, 0.0));
			if (this.flavor == Flavor.CROWD_START) {
				BlockPos arenaPos = ((CraftmineLevelDuck) level).craftmine$wardenArenaPos();
				level.playSound(null, arenaPos, CraftmineSoundEvents.VILLAGER_CROWD_START, SoundSource.MASTER, 10.0F, 1.0F);
			}
		}

		@Override
		protected void tick(ServerLevel level, E warden, long time) {
			if (this.flavor == Flavor.WORM
					&& !warden.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
					&& warden.getPose() == Pose.SLEEPING) {
				warden.setPose(Pose.STANDING);
			}
		}

		@Override
		protected void stop(ServerLevel level, E warden, long time) {
			boolean cleared = waiting(warden, this.stageIndex + 1);
			if (this.flavor == Flavor.ABORT_TO_ATTACK) {
				// the warden got bored of the intro before it began: attack!
				if (!cleared) {
					warden.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER)
						.ifPresent(player -> warden.setAttackTarget(player));
					warden.getBrain().eraseMemory(CraftmineMemoryModules.ACTING_STAGE);
					warden.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
					warden.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
				}
				return;
			}
			if (cleared) {
				if (this.pathfindingOffset != null) {
					warden.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
						new net.minecraft.world.entity.ai.memory.WalkTarget(
							warden.position().add(this.pathfindingOffset), 0.7F, 0));
				}
				warden.getBrain().setMemory(CraftmineMemoryModules.ACTING_STAGE, this.stageIndex + 1);
			} else {
				if (this.flavor == Flavor.CROWD_START && !level.players().isEmpty()) {
					level.players().get(0).sendSystemMessage(
						Component.literal("Even the Warden got too tired of waiting for the music to start..."), true);
				}
				((CraftmineWardenDuck) warden).stopActing();
			}
		}
	}

	/**
	 * The digging finale (stage 4): when cleared, builds the red-concrete
	 * backdrop wall, plays the crowd cheer, rains the "rewards" on the first
	 * player and hands the show to stage 5 (the real arena fight setup).
	 */
	static final class FinaleStage<E extends Warden> extends ActingStage<E> {

		private final Pose pose;
		private boolean rewardsDropped;

		FinaleStage(int duration, Pose pose) {
			super(duration);
			this.pose = pose;
		}

		@Override
		protected boolean checkExtraStartConditions(ServerLevel level, E warden) {
			return waiting(warden, 4);
		}

		@Override
		protected boolean canStillUse(ServerLevel level, E warden, long time) {
			return waiting(warden, 4);
		}

		@Override
		protected void start(ServerLevel level, E warden, long time) {
			warden.setPose(this.pose);
			warden.lookAt(EntityAnchorArgument.Anchor.EYES, warden.position().add(-1.0, 0.0, 0.0));
		}

		@Override
		protected void stop(ServerLevel level, E warden, long time) {
			warden.lookAt(EntityAnchorArgument.Anchor.EYES, warden.position().add(-1.0, 0.0, 0.0));
			BlockPos arenaPos = ((CraftmineLevelDuck) level).craftmine$wardenArenaPos();

			// backdrop wall: 21 long, 7 tall, red concrete powder, right behind the stage
			BlockPos wallOrigin = arenaPos.offset(0, 7, -11);
			for (int i = 0; i < 21; i++) {
				for (int j = 0; j < 7; j++) {
					level.setBlock(wallOrigin.offset(0, j, i), Blocks.CONCRETE_POWDER.red().defaultBlockState(), 2);
				}
			}

			warden.getBrain().setMemory(CraftmineMemoryModules.ACTING_STAGE, 5);
			level.playSound(null, arenaPos, CraftmineSoundEvents.VILLAGER_CROWD_CHEER, SoundSource.MASTER, 10.0F, 1.0F);

			if (!this.rewardsDropped && !level.players().isEmpty()) {
				this.rewardsDropped = true;
				net.minecraft.server.level.ServerPlayer frontRow = level.players().get(0);
				// the snapshot's "reward system" is 200 stacks of rose bushes and a wink
				for (int i = 0; i < 200; i++) {
					frontRow.drop(Items.ROSE_BUSH.getDefaultInstance().copyWithCount(64), true, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				frontRow.drop(Items.DIAMOND.getDefaultInstance().copyWithCount(5), true, net.minecraft.util.Prediction.SERVER_ONLY);
				frontRow.sendSystemMessage(Component.literal(
					"There should have been rewards, and flowers should come from the crowd, but you know... lol"), true);
			}
		}
	}
}
