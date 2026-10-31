package dev.craftmine.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;

public class AngryGhast
extends Mob
implements Enemy {
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING = SynchedEntityData.defineId(AngryGhast.class, EntityDataSerializers.BOOLEAN);
    private static final byte DEFAULT_EXPLOSION_POWER = 3;
    private int explosionPower = 3;

    public AngryGhast(EntityType<? extends AngryGhast> entityType, Level level) {
        super((EntityType<? extends Mob>)entityType, level);
        this.xpReward = 50;
        this.moveControl = new AngryGhastMoveControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new FloatTowardsPlayerGoal(this));
        this.goalSelector.addGoal(7, new AngryGhastLookGoal(this));
        this.goalSelector.addGoal(7, new AngryGhastShootFireballGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<Player>(this, Player.class, 10, true, false, null));
    }

    public boolean isCharging() {
        return this.entityData.get(DATA_IS_CHARGING);
    }

    public void setCharging(boolean bl) {
        this.entityData.set(DATA_IS_CHARGING, bl);
    }

    public int getExplosionPower() {
        return this.explosionPower;
    }

    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    private static boolean isReflectedFireball(DamageSource damageSource) {
        return damageSource.getDirectEntity() instanceof LargeFireball && damageSource.getEntity() instanceof Player;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel serverLevel, DamageSource damageSource) {
        return this.isInvulnerable() && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !AngryGhast.isReflectedFireball(damageSource) && super.isInvulnerableTo(serverLevel, damageSource);
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
        if (AngryGhast.isReflectedFireball(damageSource)) {
            return true;
        }
        if (this.isInvulnerableTo(serverLevel, damageSource)) {
            return false;
        }
        return super.hurtServer(serverLevel, damageSource, f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_CHARGING, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 200.0).add(Attributes.SCALE, 2.0).add(Attributes.FOLLOW_RANGE, 200.0);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GHAST_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.GHAST_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GHAST_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 5.0f;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    @Override
    public void addAdditionalSaveData(ValueOutput compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putByte("ExplosionPower", (byte)this.explosionPower);
    }

    @Override
    public void readAdditionalSaveData(ValueInput compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        this.explosionPower = compoundTag.getByteOr("ExplosionPower", (byte)3);
    }

    static class AngryGhastMoveControl
    extends MoveControl {
        private final AngryGhast ghast;
        private int floatDuration;

        public AngryGhastMoveControl(AngryGhast angryGhast) {
            super(angryGhast);
            this.ghast = angryGhast;
        }

        @Override
        public void tick() {
            if (this.operation != MoveControl.Operation.MOVE_TO) {
                return;
            }
            if (this.floatDuration-- <= 0) {
                this.floatDuration += this.ghast.getRandom().nextInt(5) + 2;
                Vec3 vec3 = new Vec3(this.wantedX - this.ghast.getX(), this.wantedY - this.ghast.getY(), this.wantedZ - this.ghast.getZ());
                double d = vec3.length();
                if (this.canReach(vec3 = vec3.normalize(), Mth.ceil(d))) {
                    this.ghast.setDeltaMovement(this.ghast.getDeltaMovement().add(vec3.scale(0.1)));
                } else {
                    this.operation = MoveControl.Operation.WAIT;
                }
            }
        }

        private boolean canReach(Vec3 vec3, int n) {
            AABB aABB = this.ghast.getBoundingBox();
            for (int i = 1; i < n; ++i) {
                aABB = aABB.move(vec3);
                if (this.ghast.level().noCollision(this.ghast, aABB)) continue;
                return false;
            }
            return true;
        }
    }

    static class FloatTowardsPlayerGoal
    extends Goal {
        private final AngryGhast ghast;

        public FloatTowardsPlayerGoal(AngryGhast angryGhast) {
            this.ghast = angryGhast;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            double d;
            double d2;
            MoveControl moveControl = this.ghast.getMoveControl();
            if (!moveControl.hasWanted()) {
                return true;
            }
            double d3 = moveControl.getWantedX() - this.ghast.getX();
            double d4 = d3 * d3 + (d2 = moveControl.getWantedY() - this.ghast.getY()) * d2 + (d = moveControl.getWantedZ() - this.ghast.getZ()) * d;
            return d4 < 1.0 || d4 > 3600.0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            RandomSource randomSource = this.ghast.getRandom();
            Player player = this.ghast.level().getNearestPlayer(this.ghast, 200.0);
            if (player == null) {
                double d = this.ghast.getX() + (double)((randomSource.nextFloat() * 2.0f - 1.0f) * 16.0f);
                double d2 = this.ghast.getY() + (double)((randomSource.nextFloat() * 2.0f - 1.0f) * 16.0f);
                double d3 = this.ghast.getZ() + (double)((randomSource.nextFloat() * 2.0f - 1.0f) * 16.0f);
                this.ghast.getMoveControl().setWantedPosition(d, d2, d3, 1.0);
            } else {
                BlockPos blockPos = player.blockPosition().offset(new Vec3i(randomSource.nextInt(20), randomSource.nextInt(20), randomSource.nextInt(20)));
                this.ghast.getMoveControl().setWantedPosition(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1.0);
            }
        }
    }

    static class AngryGhastLookGoal
    extends Goal {
        private final AngryGhast ghast;

        public AngryGhastLookGoal(AngryGhast angryGhast) {
            this.ghast = angryGhast;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.ghast.getTarget() == null) {
                Vec3 vec3 = this.ghast.getDeltaMovement();
                this.ghast.setYRot(-((float)Mth.atan2(vec3.x, vec3.z)) * 57.295776f);
                this.ghast.yBodyRot = this.ghast.getYRot();
            } else {
                LivingEntity livingEntity = this.ghast.getTarget();
                double d = 64.0;
                if (livingEntity.distanceToSqr(this.ghast) < 4096.0) {
                    double d2 = livingEntity.getX() - this.ghast.getX();
                    double d3 = livingEntity.getZ() - this.ghast.getZ();
                    this.ghast.setYRot(-((float)Mth.atan2(d2, d3)) * 57.295776f);
                    this.ghast.yBodyRot = this.ghast.getYRot();
                }
            }
        }
    }

    static class AngryGhastShootFireballGoal
    extends Goal {
        private final AngryGhast ghast;
        public int chargeTime;

        public AngryGhastShootFireballGoal(AngryGhast angryGhast) {
            this.ghast = angryGhast;
        }

        @Override
        public boolean canUse() {
            return this.ghast.getTarget() != null;
        }

        @Override
        public void start() {
            this.chargeTime = 0;
        }

        @Override
        public void stop() {
            this.ghast.setCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity livingEntity = this.ghast.getTarget();
            if (livingEntity == null) {
                return;
            }
            double d = 100.0;
            if (livingEntity.distanceToSqr(this.ghast) < 10000.0 && this.ghast.hasLineOfSight(livingEntity)) {
                Level level = this.ghast.level();
                ++this.chargeTime;
                if (this.chargeTime == 10 && !this.ghast.isSilent()) {
                    level.levelEvent(null, 1015, this.ghast.blockPosition(), 0);
                }
                if (this.chargeTime == 20 || this.chargeTime == 25) {
                    this.fire(livingEntity, level);
                }
                if (this.chargeTime == 35) {
                    this.fire(livingEntity, level);
                    this.chargeTime = -60;
                }
            } else if (this.chargeTime > 0) {
                --this.chargeTime;
            }
            this.ghast.setCharging(this.chargeTime > 10);
        }

        private void fire(LivingEntity livingEntity, Level level) {
            double d = 4.0;
            Vec3 vec3 = this.ghast.getViewVector(1.0f);
            double d2 = livingEntity.getX() - (this.ghast.getX() + vec3.x * 4.0);
            double d3 = livingEntity.getY(0.5) - (0.5 + this.ghast.getY(0.5));
            double d4 = livingEntity.getZ() - (this.ghast.getZ() + vec3.z * 4.0);
            Vec3 vec32 = new Vec3(d2, d3, d4);
            if (!this.ghast.isSilent()) {
                level.levelEvent(null, 1016, this.ghast.blockPosition(), 0);
            }
            LargeFireball largeFireball = new LargeFireball(level, (LivingEntity)this.ghast, vec32.normalize(), this.ghast.getExplosionPower());
            largeFireball.setPos(this.ghast.getX() + vec3.x * 4.0, this.ghast.getY(0.5) + 0.5, largeFireball.getZ() + vec3.z * 4.0);
            level.addFreshEntity(largeFireball);
        }
    }
}

