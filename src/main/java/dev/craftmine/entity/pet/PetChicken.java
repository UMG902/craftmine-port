package dev.craftmine.entity.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.animal.chicken.ChickenVariants;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import dev.craftmine.entity.pet.AbstractPet;
import dev.craftmine.registry.CraftmineLootTables;
import dev.craftmine.registry.CraftmineSoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * The pet chicken companion: a flapping follower that still lays eggs on
 * the vanilla chicken's 5-10 minute clock (from the port's own loot table),
 * glides instead of plummeting, and keeps the chicken jockey NBT flag for
 * world compatibility. Sounds come from the port's own sound registry.
 */
public class PetChicken extends AbstractPet {

    private static final EntityDataAccessor<Holder<ChickenVariant>> DATA_VARIANT_ID =
        SynchedEntityData.defineId(PetChicken.class, EntityDataSerializers.CHICKEN_VARIANT);

    /** Baby hitbox, matching the vanilla chicken's baby dimensions. */
    private static final EntityDimensions BABY_DIMENSIONS =
        EntityTypes.CHICKEN.getDimensions().scale(0.5F).withEyeHeight(0.2975F);
    /** Egg-lay timer reset value bounds: 6000-11999 ticks (5-10 minutes). */
    private static final int EGG_TIME_MIN = 6000;
    private static final int EGG_TIME_VARIANCE = 6000;
    /** Fall-glide vertical drag multiplier. */
    private static final double GLIDE_VERTICAL_DRAG = 0.6;

    // Flap animation state (public: read by the renderer)
    public float flap;
    public float flapSpeed;
    public float oFlapSpeed;
    public float oFlap;
    public float flapping = 1.0F;
    private float nextFlap = 1.0F;

    public int eggTime = this.random.nextInt(EGG_TIME_VARIANCE) + EGG_TIME_MIN;

    /** Persisted for world compatibility with vanilla chicken save data. */
    public boolean isChickenJockey = false;

    public PetChicken(EntityType<? extends PetChicken> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), ChickenVariants.TEMPERATE));
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 4.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    // ------------------------------------------------------------------
    // Tick: flapping, gliding, egg laying
    // ------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        this.oFlap = this.flap;
        this.oFlapSpeed = this.flapSpeed;
        this.flapSpeed += (this.onGround() ? -1.0F : 4.0F) * 0.3F;
        this.flapSpeed = Mth.clamp(this.flapSpeed, 0.0F, 1.0F);
        if (!this.onGround() && this.flapping < 1.0F) {
            this.flapping = 1.0F;
        }
        this.flapping *= 0.9F;
        Vec3 velocity = this.getDeltaMovement();
        if (!this.onGround() && velocity.y < 0.0) {
            this.setDeltaMovement(velocity.multiply(1.0, GLIDE_VERTICAL_DRAG, 1.0));
        }
        this.flap += this.flapping * 2.0F;
        if (this.level() instanceof ServerLevel serverLevel) {
            this.tickEggLaying(serverLevel);
        }
    }

    private void tickEggLaying(ServerLevel level) {
        if (!this.isAlive() || this.isBaby() || --this.eggTime > 0) {
            return;
        }
        if (this.dropFromGiftLootTable(level, CraftmineLootTables.CHICKEN_LAY, this::spawnAtLocation)) {
            this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            this.gameEvent(GameEvent.ENTITY_PLACE);
        }
        this.eggTime = this.random.nextInt(EGG_TIME_VARIANCE) + EGG_TIME_MIN;
    }

    @Override
    protected boolean isFlapping() {
        return this.flyDist > this.nextFlap;
    }

    @Override
    protected void onFlap() {
        this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
    }

    // ------------------------------------------------------------------
    // Sounds (port's own chicken sound set)
    // ------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return CraftmineSoundEvents.CHICKEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return CraftmineSoundEvents.CHICKEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return CraftmineSoundEvents.CHICKEN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(CraftmineSoundEvents.CHICKEN_STEP, 0.15F, 1.0F);
    }

    // ------------------------------------------------------------------
    // Food / persistence / variant plumbing
    // ------------------------------------------------------------------

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.CHICKEN_FOOD);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.isChickenJockey = input.getBooleanOr("IsChickenJockey", false);
        input.getInt("EggLayTime").ifPresent(this::setEggTime);
        VariantUtils.readVariant(input, Registries.CHICKEN_VARIANT).ifPresent(this::setVariant);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("IsChickenJockey", this.isChickenJockey);
        output.putInt("EggLayTime", this.eggTime);
        VariantUtils.writeVariant(output, this.getVariant());
    }

    private void setEggTime(int ticks) {
        this.eggTime = ticks;
    }

    public void setVariant(Holder<ChickenVariant> variant) {
        this.entityData.set(DATA_VARIANT_ID, variant);
    }

    public Holder<ChickenVariant> getVariant() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    @Override
    @Nullable
    public <T> T get(DataComponentType<? extends T> type) {
        if (type == DataComponents.CHICKEN_VARIANT) {
            return castComponentValue(type, this.getVariant());
        }
        return super.get(type);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter source) {
        this.applyImplicitComponentIfPresent(source, DataComponents.CHICKEN_VARIANT);
        super.applyImplicitComponents(source);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.CHICKEN_VARIANT) {
            Holder<ChickenVariant> variant = castComponentValue(DataComponents.CHICKEN_VARIANT, value);
            if (variant != null) {
                this.setVariant(variant);
                return true;
            }
            return false;
        }
        return super.applyImplicitComponent(type, value);
    }
}
