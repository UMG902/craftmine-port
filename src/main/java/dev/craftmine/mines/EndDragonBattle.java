package dev.craftmine.mines;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import dev.craftmine.mixin.EnderDragonFightAccessor;
import dev.craftmine.mixin.ServerLevelAccessor;

/**
 * The ender-dragon boss fight as a mine event. The first tick creates a fresh vanilla
 * {@link EnderDragonFight} on the mine's zero podium (vanilla only runs the fight in
 * the end dimension, so the port drives the fight object itself), replicates the
 * private exit-portal placement through the {@link EnderDragonFightAccessor} mixin,
 * and reports {@link Status#WON} once {@link #complete()} fires from the dragon-kill
 * hook.
 */
public class EndDragonBattle implements MineEvent {

    public static final MapCodec<EndDragonBattle> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.BOOL.fieldOf("completed").forGetter(battle -> battle.completed),
        BlockPos.CODEC.fieldOf("pos").forGetter(battle -> battle.pos)
    ).apply(instance, EndDragonBattle::new));

    private boolean completed;
    private BlockPos pos;

    public EndDragonBattle() {
        this(false, BlockPos.ZERO);
    }

    public EndDragonBattle(boolean completed, BlockPos pos) {
        this.completed = completed;
        this.pos = pos;
    }

    /** Marks the fight won; called by the dragon-kill gameplay hook. */
    public void complete() {
        this.completed = true;
    }

    @Override
    public void tick(ServerLevel level) {
        if (this.completed) {
            return;
        }
        ServerLevelAccessor levelAccess = (ServerLevelAccessor) (Object) level;
        if (levelAccess.craftmine$getDragonFight() == null) {
            EnderDragonFight fight = new EnderDragonFight(false, true, true, Optional.empty(), 0,
                Optional.empty(), Optional.empty(), new ArrayList<>(), List.of());
            fight.init(level, level.getSeed(), BlockPos.ZERO);
            levelAccess.craftmine$setDragonFight(fight);
            // vanilla spawnExitPortal is private; replicate its exit-portal placement and record it
            BlockPos podium = EnderDragonFight.getPodiumLocation(BlockPos.ZERO);
            BlockPos portalPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, podium).below();
            ((EnderDragonFightAccessor) (Object) fight).craftmine$setExitPortalLocation(portalPos);
            this.pos = portalPos;
            for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                BlockPos crystalPos = this.pos.above().relative(direction, 3);
                EndCrystal crystal = new EndCrystal(level, crystalPos.getX() + 0.5, crystalPos.getY(), crystalPos.getZ() + 0.5);
                crystal.setShowBottom(false);
                level.addFreshEntity(crystal);
            }
            levelAccess.craftmine$getDragonFight().tryRespawn();
        } else {
            BlockPos portal = ((EnderDragonFightAccessor) (Object) levelAccess.craftmine$getDragonFight())
                .craftmine$getExitPortalLocation();
            if (portal != null) {
                this.pos = portal;
            }
        }
    }

    @Override
    public void end(ServerLevel level, boolean won) {
        ServerLevelAccessor levelAccess = (ServerLevelAccessor) (Object) level;
        EnderDragonFight fight = levelAccess.craftmine$getDragonFight();
        UUID dragonUuid = fight != null ? ((EnderDragonFightAccessor) (Object) fight).craftmine$getDragonUUID() : null;
        Entity dragon = dragonUuid != null ? level.getEntity(dragonUuid) : null;
        if (dragon != null) {
            dragon.kill(level);
        }
        levelAccess.craftmine$setDragonFight(null);
    }

    @Override
    public BlockPos getPosition() {
        return this.pos;
    }

    @Override
    public MineEvent.Status getStatus() {
        return this.completed ? MineEvent.Status.WON : MineEvent.Status.ACTIVE;
    }

    @Override
    public MapCodec<EndDragonBattle> codec() {
        return CODEC;
    }
}
