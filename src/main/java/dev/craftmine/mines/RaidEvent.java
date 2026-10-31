package dev.craftmine.mines;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;

/**
 * The vanilla-raid mine event: follows the nearest raid around the anchor position,
 * updates its own position to the raid centre and flips to WON/FAILED when the raid
 * ends, which is what makes the exit structure spawn at the raid's last stand.
 */
public class RaidEvent implements MineEvent {

    public static final MapCodec<RaidEvent> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        BlockPos.CODEC.fieldOf("pos").forGetter(event -> event.position),
        MineEvent.Status.CODEC.fieldOf("win_status").forGetter(event -> event.winStatus)
    ).apply(instance, RaidEvent::new));

    private BlockPos position;
    private MineEvent.Status winStatus;

    public RaidEvent(BlockPos position, MineEvent.Status winStatus) {
        this.position = position;
        this.winStatus = winStatus;
    }

    public RaidEvent() {
        this(BlockPos.ZERO, MineEvent.Status.ACTIVE);
    }

    @Override
    public void tick(ServerLevel level) {
        Raid raid = level.getRaids().getNearbyRaid(this.position, 100_000);
        if (raid != null) {
            this.position = raid.getCenter();
            if (raid.isOver()) {
                this.winStatus = raid.isVictory() ? MineEvent.Status.WON : MineEvent.Status.FAILED;
            }
        }
    }

    @Override
    public void end(ServerLevel level, boolean won) {
    }

    @Override
    public BlockPos getPosition() {
        return this.position;
    }

    @Override
    public MineEvent.Status getStatus() {
        return this.winStatus;
    }

    @Override
    public MapCodec<RaidEvent> codec() {
        return CODEC;
    }
}
