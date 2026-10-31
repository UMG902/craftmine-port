package dev.craftmine.entity.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import dev.craftmine.entity.pet.AbstractPet;

/**
 * The pet armadillo companion: keeps the vanilla armadillo's ball-up state
 * machine (idle / unrolling / rolling / scared), the peek animation driven
 * by the client peek event, and the frozen body rotation while scared.
 * Scute collection from the vanilla mob was dropped by the fork — the
 * scute timer persists but nothing consumes it.
 */
public class PetArmadillo extends AbstractPet {

    private static final EntityDataAccessor<Armadillo.ArmadilloState> ARMADILLO_STATE =
        SynchedEntityData.defineId(PetArmadillo.class, EntityDataSerializers.ARMADILLO_STATE);

    /** Head-yaw clamp while scared (frozen to the body). */
    private static final int MAX_HEAD_Y_ROT = 32;
    private static final int MAX_HEAD_Y_ROT_SCARED = 0;

    public final AnimationState rollOutAnimationState = new AnimationState();
    public final AnimationState rollUpAnimationState = new AnimationState();
    public final AnimationState peekAnimationState = new AnimationState();

    private long inStateTicks;
    private int scuteTime;
    private boolean peekReceivedClient;

    public PetArmadillo(EntityType<? extends PetArmadillo> type, Level level) {
        super(type, level);
        this.getNavigation().setCanFloat(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 12.0)
            .add(Attributes.MOVEMENT_SPEED, 0.14);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ARMADILLO_STATE, Armadillo.ArmadilloState.IDLE);
    }

    // ------------------------------------------------------------------
    // State machine
    // ------------------------------------------------------------------

    public Armadillo.ArmadilloState getState() {
        return this.entityData.get(ARMADILLO_STATE);
    }

    public void switchToState(Armadillo.ArmadilloState state) {
        this.entityData.set(ARMADILLO_STATE, state);
    }

    public boolean isScared() {
        return this.getState() != Armadillo.ArmadilloState.IDLE;
    }

    /** True once the scared roll-up animation has fully hidden the face. */
    public boolean shouldHideInShell() {
        return this.getState().shouldHideInShell(this.inStateTicks);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (ARMADILLO_STATE.equals(accessor)) {
            this.inStateTicks = 0L;
        }
        super.onSyncedDataUpdated(accessor);
    }

    /**
     * Fork quirk kept 1:1: pet armadillos do not shrink when babies
     * (a hand-edited Age tag is the only way to get one anyway).
     */
    @Override
    public float getAgeScale() {
        return 1.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.setupAnimationStates();
        }
        if (this.isScared()) {
            this.clampHeadRotationToBody();
        }
        this.inStateTicks++;
    }

    /** Maps the synced state onto the three animation states, client-side only. */
    private void setupAnimationStates() {
        switch (this.getState()) {
            case IDLE -> this.stopAllRollAnimations();
            case UNROLLING -> {
                this.rollOutAnimationState.startIfStopped(this.tickCount);
                this.rollUpAnimationState.stop();
                this.peekAnimationState.stop();
            }
            case ROLLING -> {
                this.rollOutAnimationState.stop();
                this.rollUpAnimationState.startIfStopped(this.tickCount);
                this.peekAnimationState.stop();
            }
            case SCARED -> {
                this.rollOutAnimationState.stop();
                this.rollUpAnimationState.stop();
                if (this.peekReceivedClient) {
                    this.peekAnimationState.stop();
                    this.peekReceivedClient = false;
                }
                if (this.inStateTicks == 0L) {
                    // First scared tick: replay the peek animation from its start pose.
                    this.peekAnimationState.start(this.tickCount);
                    this.peekAnimationState.fastForward(Armadillo.ArmadilloState.SCARED.animationDuration(), 1.0F);
                } else {
                    this.peekAnimationState.startIfStopped(this.tickCount);
                }
            }
        }
    }

    private void stopAllRollAnimations() {
        this.rollOutAnimationState.stop();
        this.rollUpAnimationState.stop();
        this.peekAnimationState.stop();
    }

    /** Client peek event: byte 64, matching the vanilla armadillo protocol. */
    private static final byte EVENT_PEEK = 64;

    @Override
    public void handleEntityEvent(byte event) {
        if (event == EVENT_PEEK && this.level().isClientSide) {
            this.peekReceivedClient = true;
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(),
                SoundEvents.ARMADILLO_PEEK, this.getSoundSource(), 1.0F, 1.0F, false);
        } else {
            super.handleEntityEvent(event);
        }
    }

    // ------------------------------------------------------------------
    // Persistence (keys match the vanilla armadillo)
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("state", Armadillo.ArmadilloState.CODEC, this.getState());
        output.putInt("scute_time", this.scuteTime);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.switchToState(input.read("state", Armadillo.ArmadilloState.CODEC).orElse(Armadillo.ArmadilloState.IDLE));
        input.getInt("scute_time").ifPresent(ticks -> this.scuteTime = ticks);
    }

    // ------------------------------------------------------------------
    // Sounds / head limits / body control
    // ------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isScared() ? null : SoundEvents.ARMADILLO_AMBIENT;
    }

    @Override
    protected void playEatingSound() {
        this.makeSound(SoundEvents.ARMADILLO_EAT);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ARMADILLO_STEP, 0.15F, 1.0F);
    }

    @Override
    public int getMaxHeadYRot() {
        return this.isScared() ? MAX_HEAD_Y_ROT_SCARED : MAX_HEAD_Y_ROT;
    }

    /** While scared the body stops following the head — the ball is rigid. */
    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                if (!PetArmadillo.this.isScared()) {
                    super.clientTick();
                }
            }
        };
    }
}
