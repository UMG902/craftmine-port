package dev.craftmine.mines;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persisted snapshot of the {@link MineEvent} running in the current mine: whether it
 * has started and the serialized event list. Written by the level data mixin so battles
 * survive server restarts.
 */
public record MineEventData(boolean started, List<MineEvent> events) {

    public static final Codec<MineEventData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.fieldOf("started").forGetter(MineEventData::started),
        MineEvent.CODEC.listOf().fieldOf("events").forGetter(MineEventData::events)
    ).apply(instance, MineEventData::new));
}
