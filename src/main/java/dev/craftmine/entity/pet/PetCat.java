package dev.craftmine.entity.pet;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.feline.CatVariants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import dev.craftmine.entity.pet.AbstractPet;
import dev.craftmine.registry.CraftmineSoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * The pet cat companion: a lazy follower with the vanilla cat's collar
 * plumbing, purr/purreow ambience when tamed, and the hiss threat sound.
 * Strays never occur (pets spawn pre-tamed) but the stray ambient sound is
 * kept for the untamed edge case. Sounds come from the port's own registry.
 */
public class PetCat extends AbstractPet {

    private static final EntityDataAccessor<Holder<CatVariant>> DATA_VARIANT_ID =
        SynchedEntityData.defineId(PetCat.class, EntityDataSerializers.CAT_VARIANT);
    private static final EntityDataAccessor<Integer> DATA_COLLAR_COLOR =
        SynchedEntityData.defineId(PetCat.class, EntityDataSerializers.INT);

    private static final ResourceKey<CatVariant> DEFAULT_VARIANT = CatVariants.BLACK;
    private static final DyeColor DEFAULT_COLLAR_COLOR = DyeColor.RED;

    /** Cats idle-voice every 6 seconds. */
    private static final int AMBIENT_SOUND_INTERVAL = 120;

    public PetCat(EntityType<? extends PetCat> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 2.0));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), DEFAULT_VARIANT));
        builder.define(DATA_COLLAR_COLOR, DEFAULT_COLLAR_COLOR.getId());
    }

    // ------------------------------------------------------------------
    // Variant + collar plumbing
    // ------------------------------------------------------------------

    public Holder<CatVariant> getVariant() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    private void setVariant(Holder<CatVariant> variant) {
        this.entityData.set(DATA_VARIANT_ID, variant);
    }

    public DyeColor getCollarColor() {
        return DyeColor.byId(this.entityData.get(DATA_COLLAR_COLOR));
    }

    private void setCollarColor(DyeColor color) {
        this.entityData.set(DATA_COLLAR_COLOR, color.getId());
    }

    @Override
    @Nullable
    public <T> T get(DataComponentType<? extends T> type) {
        if (type == DataComponents.CAT_VARIANT) {
            return castComponentValue(type, this.getVariant());
        }
        if (type == DataComponents.CAT_COLLAR) {
            return castComponentValue(type, this.getCollarColor());
        }
        return super.get(type);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter source) {
        this.applyImplicitComponentIfPresent(source, DataComponents.CAT_VARIANT);
        this.applyImplicitComponentIfPresent(source, DataComponents.CAT_COLLAR);
        super.applyImplicitComponents(source);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.CAT_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.CAT_VARIANT, value));
            return true;
        }
        if (type == DataComponents.CAT_COLLAR) {
            this.setCollarColor(castComponentValue(DataComponents.CAT_COLLAR, value));
            return true;
        }
        return super.applyImplicitComponent(type, value);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        VariantUtils.writeVariant(output, this.getVariant());
        output.store("CollarColor", DyeColor.LEGACY_ID_CODEC, this.getCollarColor());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        VariantUtils.readVariant(input, Registries.CAT_VARIANT).ifPresent(this::setVariant);
        this.setCollarColor(input.read("CollarColor", DyeColor.LEGACY_ID_CODEC).orElse(DEFAULT_COLLAR_COLOR));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        VariantUtils.selectVariantToSpawn(SpawnContext.create(level, this.blockPosition()), Registries.CAT_VARIANT)
            .ifPresent(this::setVariant);
        return data;
    }

    // ------------------------------------------------------------------
    // Sounds
    // ------------------------------------------------------------------

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        if (this.isTame()) {
            if (this.isInLove()) {
                return CraftmineSoundEvents.CAT_PURR;
            }
            if (this.random.nextInt(4) == 0) {
                return CraftmineSoundEvents.CAT_PURREOW;
            }
            return CraftmineSoundEvents.CAT_AMBIENT;
        }
        return CraftmineSoundEvents.CAT_STRAY_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return AMBIENT_SOUND_INTERVAL;
    }

    /** Threat display, invoked by unlock-tree interactions. */
    public void hiss() {
        this.makeSound(CraftmineSoundEvents.CAT_HISS);
    }

    // ------------------------------------------------------------------
    // Misc
    // ------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 10.0)
            .add(Attributes.MOVEMENT_SPEED, 0.2F)
            .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.CAT_FOOD);
    }

    /** Pets persist regardless of distance — the unlock paid for a permanent companion. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
