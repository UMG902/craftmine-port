package dev.craftmine.mines;

import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import dev.craftmine.registry.CraftmineRegistries;

/**
 * A live, tickable event inside the current mine (boss battle, raid, ...). Started by
 * world effects through the level duck's {@code startEvent}, ticked once per server
 * tick by the level mixin, and persisted through {@link MineEventData} so running
 * battles survive server restarts.
 *
 * <p>Concrete events self-serialize via their {@link #codec()}; the dispatch codec
 * resolves the implementation from the mine-event-type registry, keyed by the ids
 * registered in {@link #bootstrap}.</p>
 */
public interface MineEvent {

    Codec<MineEvent> CODEC = CraftmineRegistries.MINE_EVENT_TYPE.byNameCodec()
        .dispatch(MineEvent::codec, Function.identity());

    /** Advances the event one server tick. */
    void tick(ServerLevel level);

    /** Tears the event down; {@code won} mirrors the final {@link #getStatus()}. */
    void end(ServerLevel level, boolean won);

    /** Anchor position of the event (boss arena centre, raid position, ...). */
    BlockPos getPosition();

    Status getStatus();

    MapCodec<? extends MineEvent> codec();

    /** Registers every built-in event codec; called from the mod's registry bootstrap. */
    static MapCodec<? extends MineEvent> bootstrap(Registry<MapCodec<? extends MineEvent>> registry) {
        Registry.register(registry, "end_dragon_battle", EndDragonBattle.CODEC);
        Registry.register(registry, "raid", RaidEvent.CODEC);
        Registry.register(registry, "elder_guardian_battle", ElderGuardianBattle.CODEC);
        Registry.register(registry, "breeze_boss_battle", BreezeBossBattle.CODEC);
        return Registry.register(registry, "battle", Battle.CODEC);
    }

    /** Lifecycle of a mine event, persisted by name. */
    enum Status implements StringRepresentable {
        ACTIVE,
        WON,
        FAILED;

        public static final Codec<Status> CODEC = StringRepresentable.fromEnum(Status::values);

        @Override
        public String getSerializedName() {
            return this.name();
        }
    }
}
