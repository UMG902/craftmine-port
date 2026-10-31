package dev.craftmine.entity.pet;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import dev.craftmine.entity.pet.AbstractPet;
import org.jetbrains.annotations.Nullable;

/**
 * The pet mooshroom companion: walks on mycelium with gusto, flips between
 * the red and brown variant when struck by lightning (once per bolt), and
 * keeps the suspicious-stew effects slot of the vanilla mooshroom for save
 * compatibility. The stew milking interaction was dropped by the fork —
 * the field round-trips but nothing serves stew from a pet.
 */
public class PetMushroomCow extends AbstractPet {

    private static final EntityDataAccessor<Integer> DATA_VARIANT_ID =
        SynchedEntityData.defineId(PetMushroomCow.class, EntityDataSerializers.INT);

    private static final String TAG_VARIANT = "Type";
    private static final String TAG_STEW_EFFECTS = "stew_effects";

    @Nullable
    private SuspiciousStewEffects stewEffects;
    @Nullable
    private UUID lastLightningBoltUUID;

    public PetMushroomCow(EntityType<? extends PetMushroomCow> type, Level level) {
        super(type, level);
    }

    /** Mycelium is prime grazing ground (vanilla mooshroom pathing). */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        if (level.getBlockState(pos.below()).is(Blocks.MYCELIUM)) {
            return 10.0F;
        }
        return level.getPathfindingCostFromLightLevels(pos);
    }

    /** One flip per lightning bolt: red becomes brown, brown becomes red. */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
        UUID boltId = bolt.getUUID();
        if (boltId.equals(this.lastLightningBoltUUID)) {
            return;
        }
        this.setVariant(this.getVariant() == MushroomCow.Variant.RED
            ? MushroomCow.Variant.BROWN
            : MushroomCow.Variant.RED);
        this.lastLightningBoltUUID = boltId;
        this.playSound(SoundEvents.MOOSHROOM_CONVERT, 2.0F, 1.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT_ID, MushroomCow.Variant.DEFAULT.id());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariant(input.read(TAG_VARIANT, MushroomCow.Variant.CODEC).orElse(MushroomCow.Variant.DEFAULT));
        this.stewEffects = input.read(TAG_STEW_EFFECTS, SuspiciousStewEffects.CODEC).orElse(null);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(TAG_VARIANT, MushroomCow.Variant.CODEC, this.getVariant());
        if (this.stewEffects != null) {
            output.store(TAG_STEW_EFFECTS, SuspiciousStewEffects.CODEC, this.stewEffects);
        }
    }

    public MushroomCow.Variant getVariant() {
        return MushroomCow.Variant.byId(this.entityData.get(DATA_VARIANT_ID));
    }

    private void setVariant(MushroomCow.Variant variant) {
        this.entityData.set(DATA_VARIANT_ID, variant.id());
    }

    @Override
    @Nullable
    public <T> T get(DataComponentType<? extends T> type) {
        if (type == DataComponents.MOOSHROOM_VARIANT) {
            return castComponentValue(type, this.getVariant());
        }
        return super.get(type);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter source) {
        this.applyImplicitComponentIfPresent(source, DataComponents.MOOSHROOM_VARIANT);
        super.applyImplicitComponents(source);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.MOOSHROOM_VARIANT) {
            MushroomCow.Variant variant = castComponentValue(DataComponents.MOOSHROOM_VARIANT, value);
            if (variant != null) {
                this.setVariant(variant);
                return true;
            }
            return false;
        }
        return super.applyImplicitComponent(type, value);
    }
}
