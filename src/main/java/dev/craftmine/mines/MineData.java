package dev.craftmine.mines;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-level saved state for the mine gameplay loop: whether the level is a mine, its
 * win/fail state, the leave countdown after finishing, the XP and key accumulators,
 * the travelling-block teleport request and the players who died inside the mine.
 *
 * <p>Attached to the server level's data storage under {@code minecraft:mine_data};
 * the server level mixin drives every transition and marks the data dirty on change.
 * The codec field names double as the on-disk NBT keys — do not rename them.</p>
 */
public class MineData extends SavedData {

    private static final String LEVEL_MINE_FILE_ID = "mine_data";
    public static final int LEAVE_COUNTDOWN = 200;

    public static final Codec<MineData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.fieldOf("is_mine").forGetter(data -> data.isMine),
        Codec.BOOL.fieldOf("has_placed_start_structures").forGetter(data -> data.hasPlacedStartStructures),
        StringRepresentable.fromEnum(MineState::values).fieldOf("mine_state").forGetter(data -> data.mineState),
        Codec.INT.fieldOf("leave_countdown").forGetter(data -> data.leaveCountdown),
        Codec.INT.fieldOf("experience_to_drop").forGetter(data -> data.experienceToDrop),
        Codec.INT.fieldOf("keys_to_roll").forGetter(data -> data.keysToRoll),
        BlockPos.CODEC.optionalFieldOf("travelling_block_activated").forGetter(data -> data.travellingBlockActivated),
        Codec.list(UUIDUtil.CODEC).fieldOf("dead_players").forGetter(data -> data.deadPlayers)
    ).apply(instance, MineData::new));

    public static final SavedDataType<MineData> TYPE = new SavedDataType<>(
        Identifier.withDefaultNamespace(LEVEL_MINE_FILE_ID), MineData::new, CODEC, DataFixTypes.SAVED_DATA_GAME_RULES);

    private boolean isMine;
    private boolean hasPlacedStartStructures;
    private MineState mineState = MineState.ONGOING;
    private int leaveCountdown = LEAVE_COUNTDOWN;
    private int experienceToDrop = 0;
    private int keysToRoll = 0;
    private Optional<BlockPos> travellingBlockActivated = Optional.empty();
    private List<UUID> deadPlayers = new ArrayList<>();

    public MineData() {
        this.setDirty();
    }

    private MineData(boolean isMine, boolean hasPlacedStartStructures, MineState mineState, int leaveCountdown,
            int experienceToDrop, int keysToRoll, Optional<BlockPos> travellingBlockActivated, List<UUID> deadPlayers) {
        this.isMine = isMine;
        this.hasPlacedStartStructures = hasPlacedStartStructures;
        this.mineState = mineState;
        this.leaveCountdown = leaveCountdown;
        this.keysToRoll = keysToRoll;
        this.travellingBlockActivated = travellingBlockActivated;
        this.experienceToDrop = experienceToDrop;
        this.deadPlayers.addAll(deadPlayers);
    }

    public boolean isMine() {
        return this.isMine;
    }

    public void setMine(boolean isMine) {
        this.isMine = isMine;
        this.setDirty();
    }

    public boolean hasPlacedStartStructures() {
        return this.hasPlacedStartStructures;
    }

    public void setHasPlacedStartStructures(boolean hasPlacedStartStructures) {
        this.hasPlacedStartStructures = hasPlacedStartStructures;
        this.setDirty();
    }

    public MineState getMineState() {
        return this.mineState;
    }

    public void setMineState(MineState state) {
        this.mineState = state;
        this.setDirty();
    }

    public int getLeaveCountdown() {
        return this.leaveCountdown;
    }

    public void resetCountdown() {
        this.setDirty();
        this.leaveCountdown = LEAVE_COUNTDOWN;
    }

    /** Decrements the leave countdown; {@code true} once it has run out. */
    public boolean countDown() {
        this.setDirty();
        return --this.leaveCountdown <= 0;
    }

    /**
     * Activates or cancels the travelling block request. Returns {@code true} when the
     * request was newly placed, {@code false} when an existing request was cancelled
     * (the countdown restarts either way on cancel).
     */
    public boolean toggledMineTravellingBlock(BlockPos pos) {
        this.setDirty();
        if (this.travellingBlockActivated.isPresent()) {
            this.travellingBlockActivated = Optional.empty();
            this.resetCountdown();
            return false;
        }
        this.travellingBlockActivated = Optional.of(pos);
        return true;
    }

    public void resetMineTravellingBlock() {
        this.setDirty();
        this.travellingBlockActivated = Optional.empty();
        this.resetCountdown();
    }

    public int getExperienceToDrop() {
        return this.experienceToDrop;
    }

    public void addExperienceToDrop(int amount) {
        this.experienceToDrop += amount;
    }

    public void resetExperienceToDrop() {
        this.experienceToDrop = 0;
    }

    public int getKeysToRoll() {
        return this.keysToRoll;
    }

    public void addKeysToRoll(int amount) {
        this.keysToRoll += amount;
    }

    public void resetKeysToRoll() {
        this.keysToRoll = 0;
    }

    public Optional<BlockPos> getTravellingBlockActivated() {
        return this.travellingBlockActivated;
    }

    public void addDeadPlayer(UUID playerId) {
        if (!this.deadPlayers.contains(playerId)) {
            this.deadPlayers.add(playerId);
        }
    }

    public boolean hasPlayerDied(UUID playerId) {
        return this.deadPlayers.contains(playerId);
    }

    /** Outcome of the mine, persisted by name. */
    public enum MineState implements StringRepresentable {
        ONGOING,
        WON,
        FAILED;

        @Override
        public String getSerializedName() {
            return this.name();
        }
    }
}
