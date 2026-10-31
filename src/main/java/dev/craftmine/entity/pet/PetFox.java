package dev.craftmine.entity.pet;

import java.util.List;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import dev.craftmine.entity.pet.AbstractPet;
import org.jetbrains.annotations.Nullable;

/**
 * The pet fox companion: a crouchy little follower with the vanilla fox's
 * screech-at-night ambience, snow-biome variant selection, and the
 * head-until-uncrouched look control. Loot pickup in the main hand stays
 * enabled (the vanilla fox's item-snatching habit), so a pet fox can still
 * be outfitted by dropping things near it.
 */
public class PetFox extends AbstractPet {

    private static final EntityDataAccessor<Integer> DATA_VARIANT_ID =
        SynchedEntityData.defineId(PetFox.class, EntityDataSerializers.INT);

    /** Radius of the night-screech player scan. */
    private static final double SCREECH_SCAN_RADIUS = 16.0;
    /** Chance per tick of a random aggro yip. */
    private static final float AGGRO_YIP_CHANCE = 0.05F;
    /** Chance (while dark, no players nearby) of the dramatic screech. */
    private static final float SCREECH_CHANCE = 0.1F;

    public PetFox(EntityType<? extends PetFox> type, Level level) {
        super(type, level);
        this.lookControl = new FoxLookControl();
        this.moveControl = new MoveControl(this);
        this.setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, 0.0F);
        this.setPathfindingMalus(PathType.DAMAGING, 0.0F);
        this.getNavigation().setRequiredPathLength(32.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT_ID, Fox.Variant.DEFAULT.getId());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MOVEMENT_SPEED, 0.3F)
            .add(Attributes.MAX_HEALTH, 10.0)
            .add(Attributes.ATTACK_DAMAGE, 2.0)
            .add(Attributes.SAFE_FALL_DISTANCE, 5.0)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    // ------------------------------------------------------------------
    // Ambient behavior
    // ------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.random.nextFloat() < AGGRO_YIP_CHANCE) {
            this.playSound(SoundEvents.FOX_AGGRO, 1.0F, 1.0F);
        }
    }

    /**
     * Night ambience: alone in the dark, a pet fox may SCREECH (twice the
     * volume of a normal ambient sound).
     */
    @Override
    public void playAmbientSound() {
        SoundEvent sound = this.getAmbientSound();
        if (sound == SoundEvents.FOX_SCREECH) {
            this.playSound(sound, 2.0F, this.getVoicePitch());
        } else {
            super.playAmbientSound();
        }
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        if (!this.level().isBrightOutside() && this.random.nextFloat() < SCREECH_CHANCE) {
            List<Player> nearbyPlayers = this.level().getEntitiesOfClass(
                Player.class, this.getBoundingBox().inflate(SCREECH_SCAN_RADIUS, SCREECH_SCAN_RADIUS, SCREECH_SCAN_RADIUS),
                EntitySelector.NO_SPECTATORS);
            if (nearbyPlayers.isEmpty()) {
                return SoundEvents.FOX_SCREECH;
            }
        }
        return SoundEvents.FOX_AMBIENT;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.55F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }

    // ------------------------------------------------------------------
    // Variant + component plumbing
    // ------------------------------------------------------------------

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        Holder<Biome> biome = level.getBiome(this.blockPosition());
        this.setVariant(Fox.Variant.byBiome(biome));
        this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    public Fox.Variant getVariant() {
        return Fox.Variant.byId(this.entityData.get(DATA_VARIANT_ID));
    }

    private void setVariant(Fox.Variant variant) {
        this.entityData.set(DATA_VARIANT_ID, variant.getId());
    }

    @Override
    @Nullable
    public <T> T get(DataComponentType<? extends T> type) {
        if (type == DataComponents.FOX_VARIANT) {
            return castComponentValue(type, this.getVariant());
        }
        return super.get(type);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter source) {
        this.applyImplicitComponentIfPresent(source, DataComponents.FOX_VARIANT);
        super.applyImplicitComponents(source);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.FOX_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.FOX_VARIANT, value));
            return true;
        }
        return super.applyImplicitComponent(type, value);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Type", Fox.Variant.CODEC, this.getVariant());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariant(input.read("Type", Fox.Variant.CODEC).orElse(Fox.Variant.DEFAULT));
    }

    // ------------------------------------------------------------------
    // Equipment (foxes hold things in their mouths)
    // ------------------------------------------------------------------

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND && this.canPickUpLoot();
    }

    // ------------------------------------------------------------------
    // Fox control feel
    // ------------------------------------------------------------------

    /** The fox keeps its head level unless it is sneaking around. */
    class FoxLookControl extends LookControl {
        FoxLookControl() {
            super(PetFox.this);
        }

        @Override
        protected boolean resetXRotOnTick() {
            return !PetFox.this.isCrouching();
        }
    }

    /**
     * The vanilla fox refuses to move while sleeping; the pet fox is always
     * free to move, so the stock control is used directly.
     */
    // moveControl assigned in the constructor as a plain MoveControl
}
