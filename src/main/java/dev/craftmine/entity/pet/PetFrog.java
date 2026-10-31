package dev.craftmine.entity.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.frog.FrogVariant;
import net.minecraft.world.entity.animal.frog.FrogVariants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import dev.craftmine.entity.pet.AbstractPet;
import org.jetbrains.annotations.Nullable;

/**
 * The pet frog companion: an amphibious follower with the vanilla frog's
 * swim rig (amphibious navigation that prefers jumping over water borders),
 * the pose-driven animation states, and reduced fall damage. Variant is
 * chosen at spawn and persisted like the vanilla frog's.
 *
 * <p>The vanilla frog's brain/sensor/tongue-hunting machinery is gone — pets
 * follow their owner instead — but the frog's characteristic look control
 * (freeze head pitch while the tongue is out) is kept.</p>
 */
public class PetFrog extends AbstractPet {

    private static final EntityDataAccessor<Holder<FrogVariant>> DATA_VARIANT_ID =
        SynchedEntityData.defineId(PetFrog.class, EntityDataSerializers.FROG_VARIANT);
    private static final EntityDataAccessor<java.util.OptionalInt> DATA_TONGUE_TARGET_ID =
        SynchedEntityData.defineId(PetFrog.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);

    private static final ResourceKey<FrogVariant> DEFAULT_VARIANT = FrogVariants.TEMPERATE;
    /** Frogs shrug off this many points of fall damage. */
    private static final int FALL_DAMAGE_REDUCTION = 5;

    // Animation states (public: polled by the renderer)
    public final AnimationState jumpAnimationState = new AnimationState();
    public final AnimationState croakAnimationState = new AnimationState();
    public final AnimationState tongueAnimationState = new AnimationState();
    public final AnimationState swimIdleAnimationState = new AnimationState();

    public PetFrog(EntityType<? extends AbstractPet> type, Level level) {
        super(type, level);
        this.lookControl = new FrogLookControl();
        this.setPathfindingMalus(PathType.WATER, 4.0F);
        this.setPathfindingMalus(PathType.TRAPDOOR, -1.0F);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 8.0F, 2.0F));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), DEFAULT_VARIANT));
        builder.define(DATA_TONGUE_TARGET_ID, java.util.OptionalInt.empty());
    }

    // ------------------------------------------------------------------
    // Tongue target (synced entity id; kept for renderer/pose parity)
    // ------------------------------------------------------------------

    public void eraseTongueTarget() {
        this.entityData.set(DATA_TONGUE_TARGET_ID, java.util.OptionalInt.empty());
    }

    public java.util.Optional<Entity> getTongueTarget() {
        return this.entityData.get(DATA_TONGUE_TARGET_ID).stream()
            .mapToObj(this.level()::getEntity)
            .filter(java.util.Objects::nonNull)
            .findFirst();
    }

    public void setTongueTarget(Entity target) {
        this.entityData.set(DATA_TONGUE_TARGET_ID, java.util.OptionalInt.of(target.getId()));
    }

    // ------------------------------------------------------------------
    // Head rotation limits (frog look feel)
    // ------------------------------------------------------------------

    @Override
    public int getHeadRotSpeed() {
        return 35;
    }

    @Override
    public int getMaxHeadYRot() {
        return 5;
    }

    // ------------------------------------------------------------------
    // Variant + component plumbing
    // ------------------------------------------------------------------

    public Holder<FrogVariant> getVariant() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    private void setVariant(Holder<FrogVariant> variant) {
        this.entityData.set(DATA_VARIANT_ID, variant);
    }

    @Override
    @Nullable
    public <T> T get(DataComponentType<? extends T> type) {
        if (type == DataComponents.FROG_VARIANT) {
            return castComponentValue(type, this.getVariant());
        }
        return super.get(type);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter source) {
        this.applyImplicitComponentIfPresent(source, DataComponents.FROG_VARIANT);
        super.applyImplicitComponents(source);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.FROG_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.FROG_VARIANT, value));
            return true;
        }
        return super.applyImplicitComponent(type, value);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        VariantUtils.writeVariant(output, this.getVariant());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        VariantUtils.readVariant(input, Registries.FROG_VARIANT).ifPresent(this::setVariant);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        VariantUtils.selectVariantToSpawn(SpawnContext.create(level, this.blockPosition()), Registries.FROG_VARIANT)
            .ifPresent(this::setVariant);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    // ------------------------------------------------------------------
    // Animations
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        if (this.level().isClientSide()) {
            this.swimIdleAnimationState.animateWhen(this.isInWater() && !this.walkAnimation.isMoving(), this.tickCount);
        }
        super.tick();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (DATA_POSE.equals(accessor)) {
            Pose pose = this.getPose();
            // start/stop driven purely by pose transitions:
            if (pose == Pose.LONG_JUMPING) {
                this.jumpAnimationState.start(this.tickCount);
            } else {
                this.jumpAnimationState.stop();
            }
            if (pose == Pose.CROAKING) {
                this.croakAnimationState.start(this.tickCount);
            } else {
                this.croakAnimationState.stop();
            }
            if (pose == Pose.USING_TONGUE) {
                this.tongueAnimationState.start(this.tickCount);
            } else {
                this.tongueAnimationState.stop();
            }
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    protected void updateWalkAnimation(float partial) {
        // Freeze the walk cycle while the jump animation owns the legs.
        float speed = this.jumpAnimationState.isStarted() ? 0.0F : Math.min(partial * 25.0F, 1.0F);
        this.walkAnimation.update(speed, 0.4F, this.isBaby() ? 3.0F : 1.0F);
    }

    // ------------------------------------------------------------------
    // Sounds / movement feel
    // ------------------------------------------------------------------

    @Override
    public void playEatingSound() {
        this.level().playSound(null, this, SoundEvents.FROG_EAT, SoundSource.NEUTRAL, 2.0F, 1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MOVEMENT_SPEED, 3.0)
            .add(Attributes.MAX_HEALTH, 10.0)
            .add(Attributes.ATTACK_DAMAGE, 10.0)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FROG_AMBIENT;
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.FROG_HURT;
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return SoundEvents.FROG_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.FROG_STEP, 0.15F, 1.0F);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected int calculateFallDamage(double fallDistance, float damageMultiplier) {
        return super.calculateFallDamage(fallDistance, damageMultiplier) - FALL_DAMAGE_REDUCTION;
    }

    /** Frog-style swimming: self-propelled drift with heavy drag. */
    @Override
    public void travel(Vec3 travelVector) {
        if (this.isInWater()) {
            this.moveRelative(this.getSpeed(), travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FrogPathNavigation(this, level);
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    // ------------------------------------------------------------------
    // Frog navigation rig
    // ------------------------------------------------------------------

    /** Freezes head pitch while the tongue is out. */
    class FrogLookControl extends LookControl {
        FrogLookControl() {
            super(PetFrog.this);
        }

        @Override
        protected boolean resetXRotOnTick() {
            return PetFrog.this.getTongueTarget().isEmpty();
        }
    }

    /** Amphibious pathing that refuses to cut corners through water borders. */
    static class FrogPathNavigation extends AmphibiousPathNavigation {
        FrogPathNavigation(PetFrog frog, Level level) {
            super(frog, level);
        }

        @Override
        public boolean canCutCorner(PathType pathType) {
            return pathType != PathType.WATER_BORDER && super.canCutCorner(pathType);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            this.nodeEvaluator = new FrogNodeEvaluator(true);
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }
    }

    /** Lets the frog "jump to" blocks tagged FROG_PREFER_JUMP_TO from the water. */
    static class FrogNodeEvaluator extends AmphibiousNodeEvaluator {
        private final BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();

        FrogNodeEvaluator(boolean canPassDoors) {
            super(canPassDoors);
        }

        @Override
        public Node getStart() {
            if (!this.mob.isInWater()) {
                return super.getStart();
            }
            return this.getStartNode(new BlockPos(
                Mth.floor(this.mob.getBoundingBox().minX),
                Mth.floor(this.mob.getBoundingBox().minY),
                Mth.floor(this.mob.getBoundingBox().minZ)));
        }

        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            this.belowPos.set(x, y - 1, z);
            if (context.getBlockState(this.belowPos).is(BlockTags.FROG_PREFER_JUMP_TO)) {
                return PathType.OPEN;
            }
            return super.getPathType(context, x, y, z);
        }
    }
}
