/*
 * Per-level persisted active sky (the sky picked in the Dimension Control
 * screen). Tri-state (task 27):
 * - empty Optional         = never touched by the control -> the level's
 *                            default sky (snapshot defaults: overworld = code
 *                            rain, generated mines = overworld, end = end);
 * - NothingSky.INSTANCE    = the "nothing" state (emptied control slot) ->
 *                            BLACK sky on the client;
 * - any other Sky          = that sky.
 * The "nothing" sentinel mirrors the fork's save storage, which keeps a
 * present-but-empty sky per dimension, distinct from an absent key.
 */
package dev.craftmine.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import dev.craftmine.level.DimensionSpecialEffects;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class SkyData extends SavedData {
    public static final Codec<SkyData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            DimensionSpecialEffects.Sky.CODEC.optionalFieldOf("active_sky").forGetter(SkyData::activeSky)
        ).apply(instance, SkyData::new)
    );
    public static final SavedDataType<SkyData> TYPE = new SavedDataType<SkyData>(
        Identifier.withDefaultNamespace("sky_data"), SkyData::new, CODEC, DataFixTypes.SAVED_DATA_GAME_RULES
    );

    private Optional<DimensionSpecialEffects.Sky> activeSky = Optional.empty();

    public SkyData() {
        this.setDirty();
    }

    private SkyData(Optional<DimensionSpecialEffects.Sky> activeSky) {
        this.activeSky = activeSky;
    }

    public Optional<DimensionSpecialEffects.Sky> activeSky() {
        return this.activeSky;
    }

    public void setActiveSky(Optional<DimensionSpecialEffects.Sky> sky) {
        this.activeSky = sky;
        this.setDirty();
    }
}
