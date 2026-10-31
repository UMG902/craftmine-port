/*
 * ElderGuardianBattle - MineEvent for the "elder_guardian_boss" special mine
 * (unoriginal feature, config unoriginal_features - a loving 24w14potato
 * Mega Spud rip-off, craftmine style).
 *
 * Fight shape (user spec, task 78):
 * - the boss is a vanilla ELDER_GUARDIAN at twice the size (Attributes.SCALE
 *   2.0) with 160 max health, spawned at the arena centre on the event's
 *   first tick;
 * - every 20% of his health (<= 128, 96, 64, 32) he becomes INVULNERABLE and
 *   summons a wave of 5 NORMAL guardians spawned ALL AROUND the arena so the
 *   player has to hunt them down; while any wave guardian lives the boss
 *   stays shielded. 4 gates x 5 guardians = 20 guardians per fight;
 * - the intended "Mojang fashion" bug: each wave guardian has a 1% chance to
 *   spawn OUTSIDE the arena walls, forcing the player to mine prismarine to
 *   reach it while suffering the boss's vanilla Mining Fatigue curse - the
 *   bug is a feature;
 * - VISUAL INDICATOR while shielded (user requirement): the boss gets an
 *   infinite Glowing effect (outline visible through water/walls) and the
 *   boss bar turns PURPLE with a "(shielded)" name; on release it turns back
 *   BLUE. Wave guardians glow permanently (Battle house style);
 * - when the boss dies the event status becomes WON; tickEvents() then
 *   generates the mine exit jigsaw at getPosition() (the arena centre) -
 *   same victory flow as every other boss event.
 *
 * Server-side only (MineEvents tick inside the ServerLevel duck). State is
 * codec-serialized through MineEventData like Battle, so the fight survives
 * relogs; a gate whose guardians all died while unloaded self-releases.
 */
package dev.craftmine.mines;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.BossEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import dev.craftmine.CraftmineConfig;
import dev.craftmine.server.CraftmineServerHooks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.tags.FluidTags;

public class ElderGuardianBattle implements MineEvent {
    // ---- tuning (user spec) ----
    public static final int BOSS_MAX_HEALTH = 160;
    public static final float BOSS_SCALE = 2.0F;
    public static final int GATES = 4; // every 20% of the boss health
    public static final int GUARDIANS_PER_WAVE = 5;
    public static final float OUTSIDE_SPAWN_CHANCE = 0.01F; // the intended bug
    public static final int ARENA_RADIUS = 20;
    public static final int WALL_HEIGHT = 12;
    public static final int PILLARS = 8;

    private static final int GATE_STEP = BOSS_MAX_HEALTH / (GATES + 1); // 32 hp = 20%

    private final Identifier id;
    private UUID boss;
    private final List<UUID> waveMobs;
    private int gatesOpened;
    private BlockPos arenaCenter;
    private MineEvent.Status status;

    public static final MapCodec<ElderGuardianBattle> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Identifier.CODEC.fieldOf("id").forGetter(battle -> battle.id),
        UUIDUtil.CODEC.fieldOf("boss").forGetter(battle -> battle.boss),
        UUIDUtil.CODEC.listOf().fieldOf("wave_mobs").forGetter(battle -> battle.waveMobs),
        Codec.INT.fieldOf("gates_opened").forGetter(battle -> battle.gatesOpened),
        BlockPos.CODEC.fieldOf("arena_center").forGetter(battle -> battle.arenaCenter),
        MineEvent.Status.CODEC.fieldOf("status").forGetter(battle -> battle.status)
    ).apply(instance, ElderGuardianBattle::new));

    public ElderGuardianBattle(Identifier id, BlockPos arenaCenter) {
        this(id, null, List.of(), 0, arenaCenter, MineEvent.Status.ACTIVE);
    }

    public ElderGuardianBattle(Identifier id, UUID boss, List<UUID> waveMobs, int gatesOpened, BlockPos arenaCenter, MineEvent.Status status) {
        this.id = id;
        this.boss = boss;
        this.waveMobs = new ArrayList<UUID>(waveMobs);
        this.gatesOpened = gatesOpened;
        this.arenaCenter = arenaCenter;
        this.status = status;
    }

    @Override
    public void tick(ServerLevel serverLevel) {
        if (this.status != MineEvent.Status.ACTIVE) {
            this.removeBar();
            return;
        }
        if (this.boss == null) {
            this.spawnBoss(serverLevel);
            return;
        }
        Entity bossEntity = serverLevel.getEntity(this.boss);
        if (bossEntity == null) {
            if (serverLevel.isLoaded(this.arenaCenter)) {
                // chunks around the arena are loaded and the boss is gone -> it died
                this.status = MineEvent.Status.WON;
                this.removeBar();
            }
            return;
        }
        if (((net.minecraft.world.entity.LivingEntity) bossEntity).isDeadOrDying()) {
            this.status = MineEvent.Status.WON;
            this.removeBar();
            return;
        }

        boolean shielded = bossEntity.isPermanentlyInvulnerable();
        if (shielded && this.gatesOpened > 0) {
            boolean waveAlive = this.waveMobs.stream()
                .map(serverLevel::getEntity)
                .anyMatch(entity -> entity != null && entity.isAlive());
            if (!waveAlive) {
                this.releaseGate(bossEntity);
            }
        } else if (!shielded && this.gatesOpened < GATES
            && living(bossEntity).getHealth() <= (float)(BOSS_MAX_HEALTH - GATE_STEP * (this.gatesOpened + 1))) {
            this.openGate(serverLevel, bossEntity);
        }

        shielded = bossEntity.isPermanentlyInvulnerable();
        CustomBossEvent bar = this.ensureBar(serverLevel);
        bar.setName(Component.translatable(shielded ? "world.event.elder_guardian.shielded" : "world.event.elder_guardian.name"));
        bar.setMax(BOSS_MAX_HEALTH);
        bar.setValue(Math.round(living(bossEntity).getHealth()));
        bar.setColor(shielded ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.BLUE);
        bar.setPlayers(serverLevel.players());
    }

    private void spawnBoss(ServerLevel serverLevel) {
        // offset from the exact centre so a player spawning at the arena centre
        // does not spawn inside the boss's 2x-scale hitbox
        BlockPos spawnPos = this.arenaCenter.offset(6, 3, 0);
        Entity entity = EntityTypes.ELDER_GUARDIAN.spawn(serverLevel, spawnPos, EntitySpawnReason.EVENT);
        if (entity == null) {
            return; // retry next tick
        }
        AttributeInstance scale = ((net.minecraft.world.entity.LivingEntity) entity).getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(BOSS_SCALE);
        }
        AttributeInstance maxHealth = ((net.minecraft.world.entity.LivingEntity) entity).getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue((double) BOSS_MAX_HEALTH);
        }
        ((net.minecraft.world.entity.LivingEntity) entity).setHealth((float) BOSS_MAX_HEALTH);
        mob(entity).setPersistenceRequired();
        this.boss = entity.getUUID();
    }

    private static net.minecraft.world.entity.LivingEntity living(Entity entity) {
        return (net.minecraft.world.entity.LivingEntity) entity;
    }

    private static Mob mob(Entity entity) {
        return (Mob) entity;
    }

    private void openGate(ServerLevel serverLevel, Entity bossEntity) {
        this.gatesOpened++;
        bossEntity.setPermanentlyInvulnerable(true);
        ((net.minecraft.world.entity.LivingEntity) bossEntity).addEffect(new MobEffectInstance(MobEffects.GLOWING, -1), bossEntity);
        this.waveMobs.clear();
        for (int i = 0; i < GUARDIANS_PER_WAVE; i++) {
            BlockPos pos = this.pickWaveSpawnPos(serverLevel);
            Entity guardian = EntityTypes.GUARDIAN.spawn(serverLevel, pos, EntitySpawnReason.EVENT);
            if (guardian == null) {
                continue;
            }
            mob(guardian).setPersistenceRequired();
            ((net.minecraft.world.entity.LivingEntity) guardian).addEffect(new MobEffectInstance(MobEffects.GLOWING, -1), guardian);
            this.waveMobs.add(guardian.getUUID());
        }
        serverLevel.players().forEach(serverPlayer -> serverPlayer.sendSystemMessage(Component.translatable("world.event.elder_guardian.wave")));
    }

    private void releaseGate(Entity bossEntity) {
        bossEntity.setPermanentlyInvulnerable(false);
        ((net.minecraft.world.entity.LivingEntity) bossEntity).removeEffect(MobEffects.GLOWING);
        this.waveMobs.clear();
    }

    /**
     * Wave guardians spawn ALL AROUND the arena (uniform angle, random depth in
     * the water column) so the player has to find them. The intended bug: a 1%
     * chance per guardian to spawn OUTSIDE the arena wall instead, forcing the
     * player to mine prismarine (while under the boss's Mining Fatigue) to get
     * to it.
     */
    private BlockPos pickWaveSpawnPos(ServerLevel serverLevel) {
        RandomSource random = serverLevel.getRandom();
        boolean outside = random.nextFloat() < OUTSIDE_SPAWN_CHANCE;
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * (double) 6.2831855F;
            int distance = outside
                ? ARENA_RADIUS + 3 + random.nextInt(8)
                : 3 + random.nextInt(ARENA_RADIUS - 5);
            int x = this.arenaCenter.getX() + (int) Math.round(Math.cos(angle) * (double) distance);
            int z = this.arenaCenter.getZ() + (int) Math.round(Math.sin(angle) * (double) distance);
            int floor = serverLevel.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            int y = floor + 2 + random.nextInt(5);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = serverLevel.getBlockState(pos);
            if (state.isAir() || state.getFluidState().is(FluidTags.WATER)) {
                return pos;
            }
        }
        return this.arenaCenter.offset(ARENA_RADIUS - 4, 4, 0); // sane fallback inside the arena
    }

    /**
     * Builds the prismarine arena around the mine spawn: floor disc, wall ring
     * with embedded sea lanterns, and a crown of pillars (user: "a big arena
     * that has pillars"). Fully underwater (water_world mine) - with
     * fish_out_of_water active the player breathes water, so no air pockets are
     * built on purpose. Idempotent: the prismarine floor block at the centre
     * doubles as the "already built" marker.
     */
    public static BlockPos buildArena(ServerLevel serverLevel) {
        BlockPos spawn = serverLevel.getRespawnData().pos();
        int centerX = spawn.getX();
        int centerZ = spawn.getZ();
        int floorY = serverLevel.getHeight(Heightmap.Types.OCEAN_FLOOR, centerX, centerZ);
        BlockPos center = new BlockPos(centerX, floorY, centerZ);
        BlockState marker = serverLevel.getBlockState(center);
        if (marker.is(Blocks.PRISMARINE_BRICKS)) {
            return center; // already built (re-entry / reload)
        }
        RandomSource random = serverLevel.getRandom();
        // floor disc
        for (int dx = -ARENA_RADIUS - 1; dx <= ARENA_RADIUS + 1; dx++) {
            for (int dz = -ARENA_RADIUS - 1; dz <= ARENA_RADIUS + 1; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > (ARENA_RADIUS + 1) * (ARENA_RADIUS + 1)) {
                    continue;
                }
                BlockPos pos = new BlockPos(centerX + dx, floorY, centerZ + dz);
                boolean rim = distSq >= (ARENA_RADIUS - 2) * (ARENA_RADIUS - 2);
                serverLevel.setBlock(pos, (rim ? Blocks.DARK_PRISMARINE : Blocks.PRISMARINE_BRICKS).defaultBlockState(), 3);
            }
        }
        // wall ring (2 thick), height WALL_HEIGHT, sea lanterns embedded
        for (int dx = -ARENA_RADIUS - 1; dx <= ARENA_RADIUS + 1; dx++) {
            for (int dz = -ARENA_RADIUS - 1; dz <= ARENA_RADIUS + 1; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq < ARENA_RADIUS * ARENA_RADIUS || distSq > (ARENA_RADIUS + 1) * (ARENA_RADIUS + 1)) {
                    continue;
                }
                boolean lanternSpot = (Math.floorMod(dx + dz, 8) == 0);
                for (int h = 1; h <= WALL_HEIGHT; h++) {
                    BlockPos pos = new BlockPos(centerX + dx, floorY + h, centerZ + dz);
                    serverLevel.setBlock(pos, (lanternSpot && (h == 2 || h == WALL_HEIGHT - 1)
                        ? Blocks.SEA_LANTERN : Blocks.PRISMARINE_BRICKS).defaultBlockState(), 3);
                }
            }
        }
        // pillars (PILLARS around a ring at radius ARENA_RADIUS - 6, 2x2, capped with sea lanterns)
        for (int p = 0; p < PILLARS; p++) {
            double angle = (double) p * 6.2831855F / (double) PILLARS;
            int px = centerX + (int) Math.round(Math.cos(angle) * (double) (ARENA_RADIUS - 6));
            int pz = centerZ + (int) Math.round(Math.sin(angle) * (double) (ARENA_RADIUS - 6));
            int base = serverLevel.getHeight(Heightmap.Types.OCEAN_FLOOR, px, pz);
            for (int h = 1; h <= 9; h++) {
                for (int ox = 0; ox <= 1; ox++) {
                    for (int oz = 0; oz <= 1; oz++) {
                        serverLevel.setBlock(new BlockPos(px + ox, base + h, pz + oz), Blocks.DARK_PRISMARINE.defaultBlockState(), 3);
                    }
                }
            }
            serverLevel.setBlock(new BlockPos(px, base + 10, pz), Blocks.SEA_LANTERN.defaultBlockState(), 3);
            serverLevel.setBlock(new BlockPos(px + 1, base + 10, pz), Blocks.SEA_LANTERN.defaultBlockState(), 3);
            serverLevel.setBlock(new BlockPos(px, base + 10, pz + 1), Blocks.SEA_LANTERN.defaultBlockState(), 3);
            serverLevel.setBlock(new BlockPos(px + 1, base + 10, pz + 1), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        }
        return center;
    }

    private CustomBossEvent ensureBar(ServerLevel serverLevel) {
        CustomBossEvents events = CraftmineServerHooks.theGame().getCustomBossEvents();
        CustomBossEvent bar = events.get(this.id);
        if (bar == null) {
            bar = events.create(RandomSource.create(), this.id, Component.translatable("world.event.elder_guardian.name"));
        }
        return bar;
    }

    private void removeBar() {
        CustomBossEvents events = CraftmineServerHooks.theGame().getCustomBossEvents();
        CustomBossEvent bar = events.get(this.id);
        if (bar != null) {
            // Broadcast to the audience before dropping the entry - see Battle.removeBossBar
            // (26.3's CustomBossEvents.remove does not notify clients; without this the bar
            // stays on the HUD frozen after the guardian fight ends).
            bar.removeAllPlayers();
            events.remove(bar);
        }
    }

    @Override
    public void end(ServerLevel serverLevel, boolean won) {
        this.status = won ? MineEvent.Status.WON : MineEvent.Status.FAILED;
        this.removeBar();
        this.waveMobs.clear();
    }

    @Override
    public BlockPos getPosition() {
        return this.arenaCenter; // the exit jigsaw generates here on WON
    }

    @Override
    public MineEvent.Status getStatus() {
        return this.status;
    }

    @Override
    public MapCodec<? extends MineEvent> codec() {
        return CODEC;
    }

    public Identifier id() {
        return this.id;
    }

    public UUID boss() {
        return this.boss;
    }

    public List<UUID> waveMobs() {
        return this.waveMobs;
    }

    public int gatesOpened() {
        return this.gatesOpened;
    }

    public BlockPos arenaCenter() {
        return this.arenaCenter;
    }

    // referenced by the WorldEffect registration to keep the config switch in one place
    public static boolean isEnabled() {
        return CraftmineConfig.unoriginalFeatures();
    }
}
