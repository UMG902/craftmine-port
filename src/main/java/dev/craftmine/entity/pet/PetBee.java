package dev.craftmine.entity.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import dev.craftmine.entity.pet.AbstractPet;
import org.jetbrains.annotations.Nullable;

/**
 * The pet bee companion: a flight-capable follower that keeps the vanilla
 * bee's sting-then-die-a-little combat identity (poison payload, anger
 * window) but none of the hive/pollination life — pets are unlock-tree
 * spawns, so the hive goal machinery the vanilla bee drags along is gone.
 *
 * <p>Roll animation and water-drowning quirk are kept for the renderer and
 * gameplay parity; stinging calms the bee instead of killing it because
 * {@link AbstractPet#isFood} keeps it permanently non-breedable and the
 * fork's pet bee never actually dies from stinging.</p>
 */
public class PetBee extends AbstractPet implements NeutralMob {

    // ------------------------------------------------------------------
    // Synced data (bit flags + anger window)
    // ------------------------------------------------------------------
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID =
        SynchedEntityData.defineId(PetBee.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_REMAINING_ANGER_TIME =
        SynchedEntityData.defineId(PetBee.class, EntityDataSerializers.LONG);

    private static final int FLAG_ROLL = 2;
    private static final int FLAG_HAS_STUNG = 4;
    private static final int FLAG_HAS_NECTAR = 8;

    /** Anger window, mirroring the vanilla bee: 20-39 s. */
    private static final TimeUtilRange ANGER_DURATION = new TimeUtilRange(20, 39);
    /** Seconds of poison inflicted per sting, by difficulty. */
    private static final int POISON_SECONDS_NORMAL = 10;
    private static final int POISON_SECONDS_HARD = 18;
    /** Ticks submerged before the pet bee starts drowning (vanilla bees drown too). */
    private static final int DROWN_TICKS = 20;

    /** Wing-flap tuning shared with the renderer/sound layer. */
    public static final float FLAP_DEGREES_PER_TICK = 120.32113F;
    public static final int TICKS_PER_FLAP = Mth.ceil(1.4959966F);

    @Nullable
    private EntityReference<LivingEntity> angerTarget;
    private float rollAmount;
    private float rollAmountLast;
    private int underWaterTicks;

    public PetBee(EntityType<? extends PetBee> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.lookControl = new BeeLookControl();
        this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, -1.0F);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(PathType.COCOA, -1.0F);
        this.setPathfindingMalus(PathType.FENCE, -1.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte) 0);
        builder.define(DATA_REMAINING_ANGER_TIME, -1L);
    }

    /** Air counts as prime real estate for a flying follower. */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos).isAir() ? 10.0F : 0.0F;
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.addPersistentAngerSaveData(output);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.readPersistentAngerSaveData(this.level(), input);
    }

    // ------------------------------------------------------------------
    // Combat: sting = damage + poison, then calm down (never die)
    // ------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        DamageSource sting = this.damageSources().sting(this);
        boolean landed = target.hurtServer(level, sting, (int) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (!landed) {
            return false;
        }
        EnchantmentHelper.doPostAttackEffects(level, target, sting);
        if (target instanceof LivingEntity livingTarget) {
            livingTarget.setStingerCount(livingTarget.getStingerCount() + 1);
            int poisonSeconds = switch (this.level().getDifficulty()) {
                case NORMAL -> POISON_SECONDS_NORMAL;
                case HARD -> POISON_SECONDS_HARD;
                default -> 0;
            };
            if (poisonSeconds > 0) {
                livingTarget.addEffect(new MobEffectInstance(MobEffects.POISON, poisonSeconds * 20, 0), this);
            }
        }
        this.stopBeingAngry();
        this.playSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
        return true;
    }

    // ------------------------------------------------------------------
    // Ticking: roll animation + drowning guard
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        this.rollAmountLast = this.rollAmount;
        this.rollAmount = this.isRolling()
            ? Math.min(1.0F, this.rollAmount + 0.2F)
            : Math.max(0.0F, this.rollAmount - 0.24F);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.underWaterTicks = this.isInWater() ? this.underWaterTicks + 1 : 0;
        if (this.underWaterTicks > DROWN_TICKS) {
            this.hurtServer(level, this.damageSources().drown(), 1.0F);
        }
        this.updatePersistentAnger(level, false);
    }

    /** Renderer hook: eased roll amount for the barrel-roll pose. */
    public float getRollAmount(float partialTick) {
        return Mth.lerp(partialTick, this.rollAmountLast, this.rollAmount);
    }

    private boolean isRolling() {
        return this.getFlag(FLAG_ROLL);
    }

    private void setFlag(int flag, boolean set) {
        byte bits = this.entityData.get(DATA_FLAGS_ID);
        this.entityData.set(DATA_FLAGS_ID, set ? (byte) (bits | flag) : (byte) (bits & ~flag));
    }

    private boolean getFlag(int flag) {
        return (this.entityData.get(DATA_FLAGS_ID) & flag) != 0;
    }

    // ------------------------------------------------------------------
    // Flight plumbing
    // ------------------------------------------------------------------

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(BlockPos pos) {
                return !this.level.getBlockState(pos.below()).isAir();
            }
        };
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(false);
        navigation.setRequiredPathLength(48.0F);
        return navigation;
    }

    /** Vanilla-bee style: the bee turns its body, not its eyes. */
    class BeeLookControl extends LookControl {
        BeeLookControl() {
            super(PetBee.this);
        }

        @Override
        public void tick() {
            // intentionally empty — bees face wherever they fly
        }
    }

    // ------------------------------------------------------------------
    // Idle sounds / misc quirks kept from the vanilla bee
    // ------------------------------------------------------------------

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        // flying mobs make no step sounds
    }

    @Override
    @Nullable
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return null; // buzz comes from the flap loop, not the ambient slot
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    /** Quirk kept 1:1 from the fork: breeding "offspring" is a real vanilla bee. Unreachable in play (pets never breed). */
    @Override
    @Nullable
    public Bee getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityTypes.BEE.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    protected void checkFallDamage(double dy, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        // flyers don't take fall damage
    }

    @Override
    public boolean isFlapping() {
        return this.isFlying() && this.tickCount % TICKS_PER_FLAP == 0;
    }

    public boolean isFlying() {
        return !this.onGround();
    }

    @Override
    protected void jumpInLiquid(TagKey<Fluid> fluid) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.01, 0.0));
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.5F * this.getEyeHeight(), this.getBbWidth() * 0.2F);
    }

    // ------------------------------------------------------------------
    // NeutralMob plumbing
    // ------------------------------------------------------------------

    @Override
    public long getPersistentAngerEndTime() {
        return this.entityData.get(DATA_REMAINING_ANGER_TIME);
    }

    @Override
    public void setPersistentAngerEndTime(long endTime) {
        this.entityData.set(DATA_REMAINING_ANGER_TIME, endTime);
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

    /**
     * Anger filter: anything the bee can attack is fair game while angry;
     * the fork's pet bee considers every player a valid angry target.
     * (The original compared a UUID against an {@link EntityReference} —
     * a comparison that can never succeed — so the effective behavior is
     * exactly "players only", written as such here.)
     */
    @Override
    public boolean isAngryAt(LivingEntity target, ServerLevel level) {
        return this.canAttack(target) && target.getType() == EntityTypes.PLAYER;
    }

    /** Minimal anger-window holder so the bee class owns its tuning. */
    private record TimeUtilRange(int minSeconds, int maxSeconds) {
        int sample(net.minecraft.util.RandomSource random) {
            return TimeUtil.rangeOfSeconds(this.minSeconds, this.maxSeconds).sample(random);
        }
    }
}
