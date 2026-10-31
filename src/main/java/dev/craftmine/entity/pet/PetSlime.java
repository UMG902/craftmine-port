package dev.craftmine.entity.pet;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import dev.craftmine.entity.pet.AbstractPet;
import org.jetbrains.annotations.Nullable;

/**
 * The pet slime companion: a hopping, squishing follower that borrows the
 * vanilla slime's movement rig (jump drive + squish animation) without any
 * of the size system — the fork locks pet slimes to the tiniest size, and
 * this class keeps that quirk on purpose (the renderer and squish effects
 * are tuned for it).
 */
public class PetSlime extends AbstractPet {

    // ------------------------------------------------------------------
    // Synced data
    // ------------------------------------------------------------------
    private static final EntityDataAccessor<Integer> DATA_SIZE_ID =
        SynchedEntityData.defineId(PetSlime.class, EntityDataSerializers.INT);

    /** Lock size of the synced data — always the smallest slime. */
    private static final int LOCKED_SIZE = 1;

    // ------------------------------------------------------------------
    // Squish animation state (public: read by the renderer)
    // ------------------------------------------------------------------
    public float targetSquish;
    public float squish;
    public float oSquish;

    private boolean wasOnGround;

    public PetSlime(EntityType<? extends PetSlime> type, Level level) {
        super(type, level);
        this.fixupDimensions();
        this.moveControl = new SlimeJumpControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new SlimeSwimGoal(this));
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        this.goalSelector.addGoal(7, new SlimeKeepHoppingGoal(this));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    /** Slimes keep the hostile sound category even as pets (matches fork audio). */
    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SIZE_ID, LOCKED_SIZE);
    }

    /**
     * Size setter kept for save-data compatibility, but the pet is hard-locked
     * to {@link #LOCKED_SIZE} regardless of the requested value — the fork
     * quirk this port preserves. Attribute baselines mirror the vanilla slime.
     */
    public void setSize(@SuppressWarnings("unused") int requestedSize, boolean resetHealth) {
        this.entityData.set(DATA_SIZE_ID, LOCKED_SIZE);
        this.reapplyPosition();
        this.refreshDimensions();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10.0);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3F);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0);
        if (resetHealth) {
            this.setHealth(this.getMaxHealth());
        }
        this.xpReward = 1;
    }

    public int getSize() {
        return this.entityData.get(DATA_SIZE_ID);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        // "Size" stores size-1 like the vanilla slime; pets always save 0.
        output.putInt("Size", this.getSize() - 1);
        output.putBoolean("wasOnGround", this.wasOnGround);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        // +1 mirrors the vanilla slime encoding; setSize clamps to 1 anyway.
        this.setSize(input.getIntOr("Size", 0) + 1, false);
        super.readAdditionalSaveData(input);
        this.wasOnGround = input.getBooleanOr("wasOnGround", false);
    }

    public boolean isTiny() {
        return this.getSize() <= 1;
    }

    protected ParticleOptions getSquishParticle() {
        return ParticleTypes.ITEM_SLIME;
    }

    // ------------------------------------------------------------------
    // Tick: squish animation + landing effects
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        this.oSquish = this.squish;
        this.squish += (this.targetSquish - this.squish) * 0.5F;
        super.tick();
        if (this.onGround() && !this.wasOnGround) {
            this.onLanding();
        } else if (!this.onGround() && this.wasOnGround) {
            this.targetSquish = 1.0F;
        }
        this.wasOnGround = this.onGround();
        this.targetSquish *= 0.6F;
    }

    private void onLanding() {
        float ringWidth = this.getDimensions(this.getPose()).width() * 2.0F;
        float ringRadius = ringWidth / 2.0F;
        for (int i = 0; i < (int) (ringWidth * 16.0F); i++) {
            float angle = this.random.nextFloat() * ((float) Math.PI * 2);
            float dist = this.random.nextFloat() * 0.5F + 0.5F;
            float dx = Mth.sin(angle) * ringRadius * dist;
            float dz = Mth.cos(angle) * ringRadius * dist;
            this.level().addParticle(this.getSquishParticle(), this.getX() + dx, this.getY(), this.getZ() + dz, 0.0, 0.0, 0.0);
        }
        this.playSound(this.getSquishSound(), this.getSoundVolume(),
            ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
        this.targetSquish = -0.5F;
    }

    private int getJumpDelay() {
        return this.random.nextInt(20) + 10;
    }

    /** Refresh hitbox without shifting position (vanilla slime trick). */
    @Override
    public void refreshDimensions() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        super.refreshDimensions();
        this.setPos(x, y, z);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (DATA_SIZE_ID.equals(accessor)) {
            this.refreshDimensions();
            this.setYRot(this.yHeadRot);
            this.yBodyRot = this.yHeadRot;
            if (this.isInWater() && this.random.nextInt(20) == 0) {
                this.doWaterSplashEffect();
            }
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, dimensions.height() - 0.015625 * this.getSize() * scale, 0.0);
    }

    protected SoundEvent getSquishSound() {
        return SoundEvents.SLIME_SQUISH_SMALL;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F * this.getSize();
    }

    @Override
    public int getMaxHeadXRot() {
        return 0; // slimes face their movement direction only
    }

    protected boolean shouldPlayJumpSound() {
        return this.getSize() > 0;
    }

    @Override
    public void jumpFromGround() {
        Vec3 velocity = this.getDeltaMovement();
        this.setDeltaMovement(velocity.x, this.getJumpPower(), velocity.z);
        this.needsSync = true;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        // Fork quirk kept: the natural-size roll runs but setSize clamps to tiny.
        RandomSource random = level.getRandom();
        int roll = random.nextInt(3);
        if (roll < 2 && random.nextFloat() < 0.5F * difficulty.getSpecialMultiplier()) {
            roll++;
        }
        this.setSize(1 << roll, true);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    float getSoundPitch() {
        float base = this.isTiny() ? 1.4F : 0.8F;
        return ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * base;
    }

    protected SoundEvent getJumpSound() {
        return this.isTiny() ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return super.getDefaultDimensions(pose).scale(this.getSize());
    }

    // ------------------------------------------------------------------
    // Jump-drive movement control (vanilla slime rig, pet-tuned)
    // ------------------------------------------------------------------

    static class SlimeJumpControl extends MoveControl {
        private final PetSlime slime;
        private float yRot;
        private int jumpDelay;

        SlimeJumpControl(PetSlime slime) {
            super(slime);
            this.slime = slime;
            this.yRot = 180.0F * slime.getYRot() / (float) Math.PI;
        }

        public void setWantedMovement(double speed) {
            this.speedModifier = speed;
            this.operation = MoveControl.Operation.MOVE_TO;
        }

        @Override
        public void tick() {
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), this.yRot, 90.0F));
            this.mob.yHeadRot = this.mob.getYRot();
            this.mob.yBodyRot = this.mob.getYRot();
            if (this.operation != MoveControl.Operation.MOVE_TO) {
                this.mob.setZza(0.0F);
                return;
            }
            this.operation = MoveControl.Operation.WAIT;
            if (this.mob.onGround()) {
                this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                if (this.jumpDelay-- <= 0) {
                    this.jumpDelay = this.slime.getJumpDelay();
                    this.slime.getJumpControl().jump();
                    if (this.slime.shouldPlayJumpSound()) {
                        this.slime.playSound(this.slime.getJumpSound(), this.slime.getSoundVolume(), this.slime.getSoundPitch());
                    }
                } else {
                    this.slime.xxa = 0.0F;
                    this.slime.zza = 0.0F;
                    this.mob.setSpeed(0.0F);
                }
            } else {
                this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            }
        }
    }

    /** Paddle out of water by jumping, like the vanilla slime. */
    static class SlimeSwimGoal extends Goal {
        private final PetSlime slime;

        SlimeSwimGoal(PetSlime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
            slime.getNavigation().setCanFloat(true);
        }

        @Override
        public boolean canUse() {
            return (this.slime.isInWater() || this.slime.isInLava())
                && this.slime.getMoveControl() instanceof SlimeJumpControl;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.slime.getRandom().nextFloat() < 0.8F) {
                this.slime.getJumpControl().jump();
            }
            if (this.slime.getMoveControl() instanceof SlimeJumpControl control) {
                control.setWantedMovement(1.2);
            }
        }
    }

    /** Never stop hopping — the slime's idle locomotion. */
    static class SlimeKeepHoppingGoal extends Goal {
        private final PetSlime slime;

        SlimeKeepHoppingGoal(PetSlime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !this.slime.isPassenger();
        }

        @Override
        public void tick() {
            if (this.slime.getMoveControl() instanceof SlimeJumpControl control) {
                control.setWantedMovement(1.0);
            }
        }
    }
}
