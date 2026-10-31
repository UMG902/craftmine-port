package dev.craftmine.entity.pet;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import dev.craftmine.entity.pet.AbstractPet;
import org.jetbrains.annotations.Nullable;

/**
 * The pet polar bear companion: a big, skittish follower with the vanilla
 * polar bear's stand-up-and-roar attack telegraph. The cub-protection
 * swarm behavior (adults aggro when a nearby cub is threatened) is kept
 * even though pets never breed — cubs simply never occur in play.
 *
 * <p>Standing re-scales the hitbox while rearing; the warn growl has a
 * 2-second cooldown. No anger state is carried over: the vanilla bear's
 * NeutralMob plumbing was dead weight in the fork's pet version.</p>
 */
public class PetPolarBear extends AbstractPet {

    private static final EntityDataAccessor<Boolean> DATA_STANDING_ID =
        SynchedEntityData.defineId(PetPolarBear.class, EntityDataSerializers.BOOLEAN);

    /** Ticks the stand-up/rise-down animation runs for (also drives the hitbox scale). */
    private static final float STAND_ANIMATION_TICKS = 6.0F;
    /** Ticks between warning growls while closing in on a target. */
    private static final int WARNING_SOUND_INTERVAL = 40;

    private float standAnimation;
    private float standAnimationLast;
    private int warningSoundCooldown;

    public PetPolarBear(EntityType<? extends PetPolarBear> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PolarBearMeleeGoal());
        this.goalSelector.addGoal(1, new PanicGoal((PathfinderMob) this, 2.0,
            mob -> mob.isBaby() ? DamageTypeTags.PANIC_CAUSES : DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new PolarBearHurtByGoal());
        this.targetSelector.addGoal(2, new PolarBearDefendCubsGoal());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.FOLLOW_RANGE, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STANDING_ID, false);
    }

    // ------------------------------------------------------------------
    // Standing (attack telegraph)
    // ------------------------------------------------------------------

    public boolean isStanding() {
        return this.entityData.get(DATA_STANDING_ID);
    }

    public void setStanding(boolean standing) {
        this.entityData.set(DATA_STANDING_ID, standing);
    }

    /** Renderer hook: 0..1 scale of the rear-up animation. */
    public float getStandingAnimationScale(float partialTick) {
        return Mth.lerp(partialTick, this.standAnimationLast, this.standAnimation) / STAND_ANIMATION_TICKS;
    }

    private void playWarningSound() {
        if (this.warningSoundCooldown <= 0) {
            this.makeSound(SoundEvents.POLAR_BEAR_WARNING);
            this.warningSoundCooldown = WARNING_SOUND_INTERVAL;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.standAnimation != this.standAnimationLast) {
                this.refreshDimensions(); // hitbox grows while rearing
            }
            this.standAnimationLast = this.standAnimation;
            this.standAnimation = this.isStanding()
                ? Mth.clamp(this.standAnimation + 1.0F, 0.0F, STAND_ANIMATION_TICKS)
                : Mth.clamp(this.standAnimation - 1.0F, 0.0F, STAND_ANIMATION_TICKS);
        }
        if (this.warningSoundCooldown > 0) {
            this.warningSoundCooldown--;
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        if (this.standAnimation > 0.0F) {
            float rearFraction = this.standAnimation / STAND_ANIMATION_TICKS;
            return super.getDefaultDimensions(pose).scale(1.0F, 1.0F + rearFraction);
        }
        return super.getDefaultDimensions(pose);
    }

    // ------------------------------------------------------------------
    // Sounds
    // ------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isBaby() ? SoundEvents.POLAR_BEAR_AMBIENT_BABY : SoundEvents.POLAR_BEAR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.POLAR_BEAR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.POLAR_BEAR_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        this.playSound(SoundEvents.POLAR_BEAR_STEP, 0.15F, 1.0F);
    }

    @Override
    protected float getWaterSlowDown() {
        return 0.98F;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        if (groupData == null) {
            groupData = new AgeableMob.AgeableMobGroupData(1.0F);
        }
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    /** Quirk kept 1:1 from the fork: "breeding" yields a real vanilla polar bear. Unreachable in play. */
    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityTypes.POLAR_BEAR.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    public boolean isFood(net.minecraft.world.item.ItemStack stack) {
        return false;
    }

    // ------------------------------------------------------------------
    // Goals
    // ------------------------------------------------------------------

    /** Melee with the stand-up telegraph: rear, growl, then strike. */
    class PolarBearMeleeGoal extends MeleeAttackGoal {
        PolarBearMeleeGoal() {
            super(PetPolarBear.this, 1.25, true);
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (this.canPerformAttack(target)) {
                this.resetAttackCooldown();
                this.mob.doHurtTarget(MeleeAttackGoal.getServerLevel(this.mob), target);
                PetPolarBear.this.setStanding(false);
            } else if (this.mob.distanceToSqr(target) < (double) ((target.getBbWidth() + 3.0F) * (target.getBbWidth() + 3.0F))) {
                if (this.isTimeToAttack()) {
                    PetPolarBear.this.setStanding(false);
                    this.resetAttackCooldown();
                }
                if (this.getTicksUntilNextAttack() <= 10) {
                    PetPolarBear.this.setStanding(true);
                    PetPolarBear.this.playWarningSound();
                }
            } else {
                this.resetAttackCooldown();
                PetPolarBear.this.setStanding(false);
            }
        }

        @Override
        public void stop() {
            PetPolarBear.this.setStanding(false);
            super.stop();
        }
    }

    /** Alerts nearby adult pet bears when this one is hurt (cubs alert and flee). */
    class PolarBearHurtByGoal extends HurtByTargetGoal {
        PolarBearHurtByGoal() {
            super(PetPolarBear.this);
        }

        @Override
        public void start() {
            super.start();
            if (PetPolarBear.this.isBaby()) {
                this.alertOthers();
                this.stop();
            }
        }

        @Override
        protected void alertOther(Mob mob, LivingEntity target) {
            if (mob instanceof PetPolarBear && !mob.isBaby()) {
                super.alertOther(mob, target);
            }
        }
    }

    /** Adults aggro when a cub within 8x4x8 blocks is present (vanilla bear defensive logic). */
    class PolarBearDefendCubsGoal extends NearestAttackableTargetGoal<Player> {
        PolarBearDefendCubsGoal() {
            super(PetPolarBear.this, Player.class, 20, true, true, null);
        }

        @Override
        public boolean canUse() {
            if (PetPolarBear.this.isBaby()) {
                return false;
            }
            if (super.canUse()) {
                List<PetPolarBear> nearby = PetPolarBear.this.level()
                    .getEntitiesOfClass(PetPolarBear.class, PetPolarBear.this.getBoundingBox().inflate(8.0, 4.0, 8.0));
                return nearby.stream().anyMatch(Mob::isBaby);
            }
            return false;
        }

        @Override
        protected double getFollowDistance() {
            return super.getFollowDistance() * 0.5;
        }
    }
}
