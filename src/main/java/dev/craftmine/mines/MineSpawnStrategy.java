package dev.craftmine.mines;

import java.util.function.Function;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Where the player appears when entering a crafted mine. Every strategy derives the
 * spawn point from the level's respawn position — the anchor column the mine was
 * built around.
 */
public enum MineSpawnStrategy implements StringRepresentable {

    /** On top of the highest motion-blocking block of the anchor column. */
    SURFACE("surface", level -> {
        BlockPos anchor = level.getRespawnData().pos();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor);
        return Vec3.atBottomCenterOf(anchor.atY(top));
    }),
    /**
     * Underground: scans down and then up from the vertical middle of the anchor column
     * for the first spot with a sturdy floor and open headroom, falling back to the
     * column centre when no such spot exists.
     */
    CAVE("cave", level -> {
        BlockPos anchor = level.getRespawnData().pos();
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        BlockPos middle = anchor.atY((minY + maxY) / 2);
        BlockPos.MutableBlockPos cursor = middle.mutable();
        while (cursor.getY() > minY && !isValidSpawn(level, cursor)) {
            cursor.move(Direction.DOWN);
        }
        if (cursor.getY() > minY) {
            return Vec3.atBottomCenterOf(cursor);
        }
        cursor.set(middle);
        while (cursor.getY() < maxY && !isValidSpawn(level, cursor)) {
            cursor.move(Direction.UP);
        }
        if (cursor.getY() < maxY) {
            return Vec3.atBottomCenterOf(cursor);
        }
        return Vec3.atBottomCenterOf(middle);
    });

    public static final Codec<MineSpawnStrategy> CODEC = StringRepresentable.fromEnum(MineSpawnStrategy::values);

    private final String id;
    private final Function<ServerLevel, Vec3> spawn;

    MineSpawnStrategy(String id, Function<ServerLevel, Vec3> spawn) {
        this.id = id;
        this.spawn = spawn;
    }

    /** Computes the concrete spawn position inside the given mine level. */
    public Vec3 getSpawnPosition(ServerLevel level) {
        return this.spawn.apply(level);
    }

    private static boolean isValidSpawn(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
