package dev.craftmine.entity.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariants;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import dev.craftmine.duck.CraftmineDuckBridge;
import dev.craftmine.registry.CraftmineSoundEvents;
import dev.craftmine.unlock.PlayerUnlocks;
import org.jetbrains.annotations.Nullable;

/**
 * The pet wolf companion — the fork's tamed-wolf experience rebuilt on
 * {@link AbstractPet}: collar + variant plumbing, neutral-mob anger, the
 * beg-for-food head tilt, and the full get-wet/shake-off water cycle.
 *
 * <p>Unlike the vanilla wolf it can never be tamed by bones (unlock-tree
 * spawn only), never breeds, and only defends an owner who carries the
 * {@link PlayerUnlocks#SWORD_WOLF} unlock. Save keys and synced IDs match
 * the vanilla wolf so world data stays compatible.</p>
 */
public class PetWolf extends AbstractPet implements NeutralMob {

    // ------------------------------------------------------------------
    // Synced data
    // ------------------------------------------------------------------
    private static final EntityDataAccessor<Boolean> DATA_INTERESTED_ID =
        SynchedEntityData.defineId(PetWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLLAR_COLOR =
        SynchedEntityData.defineId(PetWolf.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_ANGER_END_TIME =
        SynchedEntityData.defineId(PetWolf.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Holder<WolfVariant>> DATA_VARIANT_ID =
        SynchedEntityData.defineId(PetWolf.class, EntityDataSerializers.WOLF_VARIANT);
    private static final EntityDataAccessor<Holder<WolfSoundVariant>> DATA_SOUND_VARIANT_ID =
        SynchedEntityData.defineId(PetWolf.class, EntityDataSerializers.WOLF_SOUND_VARIANT);

    // ------------------------------------------------------------------
    // Begging (head tilt)
    // ------------------------------------------------------------------
    /** Fraction per tick the head-tilt angle eases toward its target. */
    private static final float HEAD_TILT_EASE = 0.4F;
    /** Head-roll amplitude handed to the renderer, in fractions of PI. */
    private static final float HEAD_TILT_RENDER_SCALE = 0.15F;
    private float headTiltAmount;
    private float headTiltAmountLast;

    // ------------------------------------------------------------------
    // Wet/shake cycle
    // ------------------------------------------------------------------
    private static final float SHAKE_PROGRESS_PER_TICK = 0.05F;
    /** Shake animation length; at this progress the wolf is dry again. */
    private static final float SHAKE_END_PROGRESS = 2.0F;
    /** Shake progress at which water droplets start flying off. */
    private static final float SHAKE_SPLASH_START_PROGRESS = 0.4F;
    /** Droplet count scales with sin(progress * PI) * this. */
    private static final float SHAKE_SPLASH_COUNT_SCALE = 7.0F;
    /** Wetness darkens the coat to this shade, easing back to 1.0 while shaking. */
    private static final float WET_SHADE_MIN = 0.75F;
    private boolean isWet;
    private boolean isShaking;
    private float shakeProgress;
    private float shakeProgressLast;

    // ------------------------------------------------------------------
    // Anger (NeutralMob)
    // ------------------------------------------------------------------
    private static final UniformInt ANGER_DURATION = TimeUtil.rangeOfSeconds(20, 39);
    @Nullable
    private EntityReference<LivingEntity> angerTarget;

    private static final DyeColor DEFAULT_COLLAR_COLOR = DyeColor.RED;

    public PetWolf(EntityType<? extends PetWolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TamableAnimal.TamableAnimalPanicGoal(1.5, DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(9, new PetWolfBegGoal(this, 8.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(7, new NearestAttackableTargetGoal<>(this, AbstractSkeleton.class, false));
        this.targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MOVEMENT_SPEED, 0.3F)
            .add(Attributes.MAX_HEALTH, 8.0)
            .add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        Registry<WolfSoundVariant> soundVariants =
            (Registry<WolfSoundVariant>) this.registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT);
        builder.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), WolfVariants.DEFAULT));
        builder.define(DATA_SOUND_VARIANT_ID, soundVariants.get(WolfSoundVariants.CLASSIC).or(soundVariants::getAny).orElseThrow());
        builder.define(DATA_INTERESTED_ID, false);
        builder.define(DATA_COLLAR_COLOR, DEFAULT_COLLAR_COLOR.getId());
        builder.define(DATA_ANGER_END_TIME, -1L);
    }

    // ------------------------------------------------------------------
    // Variant + collar (drives textures / sounds / renderer)
    // ------------------------------------------------------------------

    /** Texture of the current variant, chosen by tame/angry/wild state. */
    public Identifier getTexture() {
        WolfVariant variant = this.getVariant().value();
        if (this.isTame()) {
            return variant.adultInfo().tame().texturePath();
        }
        if (this.isAngry()) {
            return variant.adultInfo().angry().texturePath();
        }
        return variant.adultInfo().wild().texturePath();
    }

    private Holder<WolfVariant> getVariant() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    private void setVariant(Holder<WolfVariant> variant) {
        this.entityData.set(DATA_VARIANT_ID, variant);
    }

    private Holder<WolfSoundVariant> getSoundVariant() {
        return this.entityData.get(DATA_SOUND_VARIANT_ID);
    }

    private void setSoundVariant(Holder<WolfSoundVariant> variant) {
        this.entityData.set(DATA_SOUND_VARIANT_ID, variant);
    }

    public DyeColor getCollarColor() {
        return DyeColor.byId(this.entityData.get(DATA_COLLAR_COLOR));
    }

    private void setCollarColor(DyeColor color) {
        this.entityData.set(DATA_COLLAR_COLOR, color.getId());
    }

    /** Exposes variant/collar through data components (wolf items, /data, etc.). */
    @Override
    @Nullable
    public <T> T get(DataComponentType<? extends T> type) {
        if (type == DataComponents.WOLF_VARIANT) {
            return castComponentValue(type, this.getVariant());
        }
        if (type == DataComponents.WOLF_SOUND_VARIANT) {
            return castComponentValue(type, this.getSoundVariant());
        }
        if (type == DataComponents.WOLF_COLLAR) {
            return castComponentValue(type, this.getCollarColor());
        }
        return super.get(type);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter source) {
        this.applyImplicitComponentIfPresent(source, DataComponents.WOLF_VARIANT);
        this.applyImplicitComponentIfPresent(source, DataComponents.WOLF_SOUND_VARIANT);
        this.applyImplicitComponentIfPresent(source, DataComponents.WOLF_COLLAR);
        super.applyImplicitComponents(source);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.WOLF_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.WOLF_VARIANT, value));
            return true;
        }
        if (type == DataComponents.WOLF_SOUND_VARIANT) {
            this.setSoundVariant(castComponentValue(DataComponents.WOLF_SOUND_VARIANT, value));
            return true;
        }
        if (type == DataComponents.WOLF_COLLAR) {
            this.setCollarColor(castComponentValue(DataComponents.WOLF_COLLAR, value));
            return true;
        }
        return super.applyImplicitComponent(type, value);
    }

    // ------------------------------------------------------------------
    // Persistence — keys mirror the vanilla wolf
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("CollarColor", DyeColor.LEGACY_ID_CODEC, this.getCollarColor());
        VariantUtils.writeVariant(output, this.getVariant());
        this.addPersistentAngerSaveData(output);
        this.getSoundVariant().unwrapKey()
            .ifPresent(key -> output.store("sound_variant", ResourceKey.codec(Registries.WOLF_SOUND_VARIANT), key));
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        VariantUtils.readVariant(input, Registries.WOLF_VARIANT).ifPresent(this::setVariant);
        this.setCollarColor(input.read("CollarColor", DyeColor.LEGACY_ID_CODEC).orElse(DEFAULT_COLLAR_COLOR));
        this.readPersistentAngerSaveData(this.level(), input);
        Registry<WolfSoundVariant> soundVariants = this.registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT);
        input.<ResourceKey<WolfSoundVariant>>read("sound_variant", ResourceKey.codec(Registries.WOLF_SOUND_VARIANT))
            .flatMap(soundVariants::get)
            .ifPresent(this::setSoundVariant);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        VariantUtils.selectVariantToSpawn(SpawnContext.create(level, this.blockPosition()), Registries.WOLF_VARIANT)
            .ifPresent(this::setVariant);
        this.setSoundVariant(this.registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT).getRandom(this.random).orElseThrow());
        return super.finalizeSpawn(level, difficulty, spawnReason, null);
    }

    // ------------------------------------------------------------------
    // Sounds
    // ------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        WolfSoundVariant.WolfSoundSet sounds = this.getSoundSet();
        if (this.isAngry()) {
            return sounds.growlSound().value();
        }
        if (this.random.nextInt(3) == 0) {
            if (this.isTame() && this.getHealth() < 20.0F) {
                return sounds.whineSound().value();
            }
            return sounds.pantSound().value();
        }
        return sounds.ambientSound().value();
    }

    private WolfSoundVariant.WolfSoundSet getSoundSet() {
        WolfSoundVariant variant = this.getSoundVariant().value();
        return this.isBaby() ? variant.babySounds() : variant.adultSounds();
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(CraftmineSoundEvents.WOLF_STEP, 0.15F, 1.0F);
    }

    // ------------------------------------------------------------------
    // Ticking: anger + wet/shake cycle
    // ------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.isWet && !this.isShaking && !this.isPathFinding() && this.onGround()) {
            this.startShake();
            this.level().broadcastEntityEvent(this, EntityEvent.START_SHAKING);
        }
        if (!this.level().isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level(), true);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        this.tickHeadTilt();
        this.tickShakeCycle();
    }

    /** Eases the beg head-tilt toward 1 when interested, back to 0 otherwise. */
    private void tickHeadTilt() {
        this.headTiltAmountLast = this.headTiltAmount;
        float target = this.isInterested() ? 1.0F : 0.0F;
        this.headTiltAmount += (target - this.headTiltAmount) * HEAD_TILT_EASE;
    }

    /**
     * Runs the wet -> shaking -> dry state machine server-side and drives the
     * splash particles + shake sound client-side. Getting rained on mid-shake
     * cancels the cycle (broadcast back to the server with END_SHAKING).
     */
    private void tickShakeCycle() {
        if (this.isInWaterOrRain()) {
            this.isWet = true;
            if (this.isShaking && !this.level().isClientSide) {
                this.level().broadcastEntityEvent(this, EntityEvent.STOP_SHAKING);
                this.resetShakeState();
            }
            return;
        }
        if (!this.isWet || !this.isShaking) {
            return;
        }
        if (this.shakeProgress == 0.0F) {
            this.playSound(SoundEvents.WOLF_SHAKE, this.getSoundVolume(),
                (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            this.gameEvent(GameEvent.ENTITY_ACTION);
        }
        this.shakeProgressLast = this.shakeProgress;
        this.shakeProgress += SHAKE_PROGRESS_PER_TICK;
        if (this.shakeProgressLast >= SHAKE_END_PROGRESS) {
            this.isWet = false;
            this.resetShakeState();
            return;
        }
        if (this.shakeProgress > SHAKE_SPLASH_START_PROGRESS) {
            this.spawnShakeSplashParticles();
        }
    }

    private void spawnShakeSplashParticles() {
        float droplets = Mth.sin((this.shakeProgress - SHAKE_SPLASH_START_PROGRESS) * (float) Math.PI) * SHAKE_SPLASH_COUNT_SCALE;
        Vec3 velocity = this.getDeltaMovement();
        for (int i = 0; i < (int) droplets; i++) {
            float dx = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
            float dz = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
            this.level().addParticle(ParticleTypes.SPLASH,
                this.getX() + dx, (float) this.getY() + 0.8F, this.getZ() + dz,
                velocity.x, velocity.y, velocity.z);
        }
    }

    private void startShake() {
        this.isShaking = true;
        this.shakeProgress = 0.0F;
        this.shakeProgressLast = 0.0F;
    }

    private void resetShakeState() {
        this.isShaking = false;
        this.shakeProgress = 0.0F;
        this.shakeProgressLast = 0.0F;
    }

    /** Client event bytes matching the vanilla wolf's shake protocol. */
    private static final class EntityEvent {
        static final byte START_SHAKING = 8;
        static final byte STOP_SHAKING = 56;
    }

    @Override
    public void handleEntityEvent(byte event) {
        switch (event) {
            case EntityEvent.START_SHAKING -> this.startShake();
            case EntityEvent.STOP_SHAKING -> this.resetShakeState();
            default -> super.handleEntityEvent(event);
        }
    }

    // ------------------------------------------------------------------
    // Renderer hooks (PetWolfRenderer / WolfHeldItemLayer)
    // ------------------------------------------------------------------

    /** Coat darkening while wet; eases from {@link #WET_SHADE_MIN} back to 1.0 across the shake. */
    public float getWetShade(float partialTick) {
        if (!this.isWet) {
            return 1.0F;
        }
        float shakeFraction = Mth.lerp(partialTick, this.shakeProgressLast, this.shakeProgress) / SHAKE_END_PROGRESS;
        return Math.min(WET_SHADE_MIN + shakeFraction * (1.0F - WET_SHADE_MIN), 1.0F);
    }

    public float getShakeAnim(float partialTick) {
        return Mth.lerp(partialTick, this.shakeProgressLast, this.shakeProgress);
    }

    public float getHeadRollAngle(float partialTick) {
        return Mth.lerp(partialTick, this.headTiltAmountLast, this.headTiltAmount) * HEAD_TILT_RENDER_SCALE * (float) Math.PI;
    }

    // ------------------------------------------------------------------
    // Begging (driven by PetWolfBegGoal)
    // ------------------------------------------------------------------

    public void setIsInterested(boolean interested) {
        this.entityData.set(DATA_INTERESTED_ID, interested);
    }

    public boolean isInterested() {
        return this.entityData.get(DATA_INTERESTED_ID);
    }

    @Override
    public int getMaxHeadXRot() {
        return this.isInSittingPose() ? 20 : super.getMaxHeadXRot();
    }

    // ------------------------------------------------------------------
    // Combat rules
    // ------------------------------------------------------------------

    /**
     * Decides what the pet wolf is willing to charge: it refuses targets when
     * its owner lacks the SWORD_WOLF unlock, never attacks tamed animals or
     * tamed horses, and respects PvP permissions between players.
     */
    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (owner instanceof Player ownerPlayer
            && !CraftmineDuckBridge.isActive(ownerPlayer, PlayerUnlocks.SWORD_WOLF)) {
            return false;
        }
        if (target instanceof Wolf wolfTarget) {
            return !wolfTarget.isTame() || wolfTarget.getOwner() != owner;
        }
        if (target instanceof Player targetPlayer
            && owner instanceof Player ownerPlayer2
            && !ownerPlayer2.canHarmPlayer(targetPlayer)) {
            return false;
        }
        if (target instanceof AbstractHorse horse && horse.isTamed()) {
            return false;
        }
        return !(target instanceof TamableAnimal tameable) || !tameable.isTame();
    }

    // ------------------------------------------------------------------
    // NeutralMob anger plumbing
    // ------------------------------------------------------------------

    @Override
    public long getPersistentAngerEndTime() {
        return this.entityData.get(DATA_ANGER_END_TIME);
    }

    @Override
    public void setPersistentAngerEndTime(long endTime) {
        this.entityData.set(DATA_ANGER_END_TIME, endTime);
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setTimeToRemainAngry(ANGER_DURATION.sample(this.random));
    }

    @Override
    @Nullable
    public EntityReference<LivingEntity> getPersistentAngerTarget() {
        return this.angerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> target) {
        this.angerTarget = target;
    }

    @Override
    public boolean canBeLeashed() {
        return !this.isAngry();
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.6F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }

    /** Pets exist only through the unlock tree; no breeding offspring. */
    @Override
    @Nullable
    public Wolf getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean canMate(Animal partner) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.WOLF_FOOD);
    }
}
