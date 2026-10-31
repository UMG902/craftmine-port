package dev.craftmine.entity.pet;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.cow.CowVariants;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import dev.craftmine.entity.pet.AbstractPet;
import org.jetbrains.annotations.Nullable;

/**
 * The pet cow companion: the minimal species — vanilla cow variant plumbing
 * (temperate default, biome-bucket spawn selection) on the shared pet base.
 * No goal overrides, no quirks: it just follows and looks adorable.
 */
public class PetCow extends AbstractPet {

    private static final EntityDataAccessor<Holder<CowVariant>> DATA_VARIANT_ID =
        SynchedEntityData.defineId(PetCow.class, EntityDataSerializers.COW_VARIANT);

    public PetCow(EntityType<? extends PetCow> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), CowVariants.TEMPERATE));
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        VariantUtils.writeVariant(output, this.getVariant());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        VariantUtils.readVariant(input, Registries.COW_VARIANT).ifPresent(this::setVariant);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        VariantUtils.selectVariantToSpawn(SpawnContext.create(level, this.blockPosition()), Registries.COW_VARIANT)
            .ifPresent(this::setVariant);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    public Holder<CowVariant> getVariant() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    private void setVariant(Holder<CowVariant> variant) {
        this.entityData.set(DATA_VARIANT_ID, variant);
    }

    @Override
    @Nullable
    public <T> T get(DataComponentType<? extends T> type) {
        if (type == DataComponents.COW_VARIANT) {
            return castComponentValue(type, this.getVariant());
        }
        return super.get(type);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter source) {
        this.applyImplicitComponentIfPresent(source, DataComponents.COW_VARIANT);
        super.applyImplicitComponents(source);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.COW_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.COW_VARIANT, value));
            return true;
        }
        return super.applyImplicitComponent(type, value);
    }
}
