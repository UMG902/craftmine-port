package dev.craftmine.entity.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.BinaryAnimator;
import net.minecraft.util.EasingType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import dev.craftmine.entity.pet.AbstractPet;
import org.jetbrains.annotations.Nullable;

/**
 * The pet axolotl companion: an amphibious follower with the vanilla
 * axolotl's water/ground/moving binary animators, the bucket-spawn variant
 * handling, and the "play dead" fake-out on hurt. Like all pets it never
 * breeds and (unlike the vanilla axolotl) it does not hunt anything for
 * itself — it only defends its owner.
 *
 * <p>The vanilla axolotl's brain-driven hunter/p panicking behavior is not
 * present; the PLAY_DEAD memory write is kept because the hurt reaction is
 * part of the axolotl feel the fork preserved.</p>
 */
public class PetAxolotl extends AbstractPet {

    // ------------------------------------------------------------------
    // Synced data
    // ------------------------------------------------------------------
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
        SynchedEntityData.defineId(PetAxolotl.class, EntityDataSerializers.INT);

    public static final String VARIANT_TAG = "Variant";

    // ------------------------------------------------------------------
    // Animators (public: read by the renderer)
    // ------------------------------------------------------------------
    public final BinaryAnimator inWaterAnimator = new BinaryAnimator(10, EasingType.IN_OUT_SINE);
    public final BinaryAnimator onGroundAnimator = new BinaryAnimator(10, EasingType.IN_OUT_SINE);
    public final BinaryAnimator movingAnimator = new BinaryAnimator(10, EasingType.IN_OUT_SINE);

    /** Ticks the fake-out lasts when triggered by the hurt reaction. */
    private static final int PLAY_DEAD_TICKS = 200;

    public PetAxolotl(EntityType<? extends PetAxolotl> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.moveControl = new AxolotlSwimControl(this);
        this.lookControl = new AxolotlPeekControl(this, 20);
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
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F; // fully amphibious: no terrain preference
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
    }

    // ------------------------------------------------------------------
    // Variant persistence (legacy int codec, matching the vanilla axolotl)
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(VARIANT_TAG, Axolotl.Variant.LEGACY_CODEC, this.getVariant());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariant(input.read(VARIANT_TAG, Axolotl.Variant.LEGACY_CODEC).orElse(Axolotl.Variant.DEFAULT));
    }

    public Axolotl.Variant getVariant() {
        return Axolotl.Variant.byId(this.entityData.get(DATA_VARIANT));
    }

    private void setVariant(Axolotl.Variant variant) {
        this.entityData.set(DATA_VARIANT, variant.getId());
    }

    @Override
    @Nullable
    public <T> T get(DataComponentType<? extends T> type) {
        if (type == DataComponents.AXOLOTL_VARIANT) {
            return castComponentValue(type, this.getVariant());
        }
        return super.get(type);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter source) {
        this.applyImplicitComponentIfPresent(source, DataComponents.AXOLOTL_VARIANT);
        super.applyImplicitComponents(source);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.AXOLOTL_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.AXOLOTL_VARIANT, value));
            return true;
        }
        return super.applyImplicitComponent(type, value);
    }

    // ------------------------------------------------------------------
    // Spawn-time variant selection (bucket spawns keep their variant)
    // ------------------------------------------------------------------

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        if (spawnReason == EntitySpawnReason.BUCKET) {
            return groupData;
        }
        RandomSource random = level.getRandom();
        AxolotlGroupData axolotlGroup = new AxolotlGroupData(
            Axolotl.Variant.getCommonSpawnVariant(random),
            Axolotl.Variant.getCommonSpawnVariant(random));
        this.setVariant(axolotlGroup.getVariant(random));
        return super.finalizeSpawn(level, difficulty, spawnReason, axolotlGroup);
    }

    /** Group data carrying the two common variants, vanilla-axolotl style. */
    public static class AxolotlGroupData extends AgeableMob.AgeableMobGroupData {
        private final Axolotl.Variant[] variants;

        public AxolotlGroupData(Axolotl.Variant... variants) {
            super(false);
            this.variants = variants;
        }

        public Axolotl.Variant getVariant(RandomSource random) {
            return this.variants[random.nextInt(this.variants.length)];
        }
    }

    // ------------------------------------------------------------------
    // Tick + hurt reaction
    // ------------------------------------------------------------------

    @Override
    public void baseTick() {
        super.baseTick();
        if (this.level().isClientSide()) {
            this.tickAnimators();
        }
    }

    /** Feeds the three binary animators from the current pose/motion. */
    private void tickAnimators() {
        boolean inWater = this.isInWater();
        boolean onGround = this.onGround();
        this.inWaterAnimator.tick(inWater);
        this.onGroundAnimator.tick(onGround);
        boolean moving = this.walkAnimation.isMoving() || this.getXRot() != this.xRotO || this.getYRot() != this.yRotO;
        this.movingAnimator.tick(moving);
    }

    /**
     * The vanilla axolotl's fake-out: when hurt in water by something real
     * and it would plausibly die, play dead for a short while. The RNG draw
     * order is kept exactly as the fork's (one 1-in-3 roll gates a second
     * 1-in-3 roll against the incoming damage).
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float healthBefore = this.getHealth();
        boolean realAttack = source.getEntity() != null || source.getDirectEntity() != null;
        if (!this.isNoAi()
            && this.level().random.nextInt(3) == 0
            && ((float) this.level().random.nextInt(3) < amount || healthBefore / this.getMaxHealth() < 0.5F)
            && amount < healthBefore
            && this.isInWater()
            && realAttack) {
            this.brain.setMemory(MemoryModuleType.PLAY_DEAD_TICKS, PLAY_DEAD_TICKS);
        }
        return super.hurtServer(level, source, amount);
    }

    // ------------------------------------------------------------------
    // Sounds / movement feel
    // ------------------------------------------------------------------

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.AXOLOTL_FOOD);
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 14.0)
            .add(Attributes.MOVEMENT_SPEED, 1.0)
            .add(Attributes.ATTACK_DAMAGE, 2.0)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    public void playAttackSound() {
        this.playSound(SoundEvents.AXOLOTL_ATTACK, 1.0F, 1.0F);
    }

    @Override
    public int getMaxHeadXRot() {
        return 1;
    }

    @Override
    public int getMaxHeadYRot() {
        return 1;
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AXOLOTL_HURT;
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return SoundEvents.AXOLOTL_DEATH;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return this.isInWater() ? SoundEvents.AXOLOTL_IDLE_WATER : SoundEvents.AXOLOTL_IDLE_AIR;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.AXOLOTL_SPLASH;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.AXOLOTL_SWIM;
    }

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

    /** Feeding a bucket of tropical fish leaves an empty bucket, vanilla-axolotl style. */
    @Override
    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Items.TROPICAL_FISH_BUCKET)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
        } else {
            super.usePlayerItem(player, hand, stack);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !this.hasCustomName();
    }

    // ------------------------------------------------------------------
    // Swim/look control wrappers
    // ------------------------------------------------------------------

    /** Smooth swim tuning for the pet axolotl. */
    static class AxolotlSwimControl extends SmoothSwimmingMoveControl {
        AxolotlSwimControl(PetAxolotl axolotl) {
            super(axolotl, 85, 10, 0.1F, 0.5F, false);
        }
    }

    /** Smooth look tuning for the pet axolotl. */
    class AxolotlPeekControl extends SmoothSwimmingLookControl {
        AxolotlPeekControl(PetAxolotl axolotl, int maxDeltaYRot) {
            super(axolotl, maxDeltaYRot);
        }
    }
}
