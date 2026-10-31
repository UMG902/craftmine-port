/*
 * BreezeBossBattle - MineEvent for the "breeze_boss" special mine
 * (unoriginal feature, config unoriginal_features - second boss of the
 * unoriginal toggle, sibling of ElderGuardianBattle).
 *
 * Fight shape (user spec, task 80):
 * - the boss is a vanilla BREEZE with NO AI (setNoAi), floating 50 blocks
 *   above a 3x3 obsidian platform in an otherwise completely normal plains
 *   mine. He cannot move, cannot take knockback, and setPermanentlyInvulnerable
 *   is the "give it resistance or smth so attacks don't do shit" - every
 *   conventional attack reads as invulnerable; the only interaction is the
 *   mace smash;
 * - SMASH = the vanilla MACE_SMASH damage type (detected in
 *   CraftmineBreezeBossMixins$LivingEntityMixin and routed here through
 *   onBossSmashed). Smash damage is NOT dealt to the boss - it accumulates:
 *   EVERY 50 DAMAGE = 5 BLOCKS DOWN (teleport, deterministic, no drift; the
 *   boss is pinned to its anchor every tick). 50 blocks / 5 = 10 descents to
 *   the ground;
 * - PLATFORM CHECK runs after every descent ONLY (user: no per-tick scans -
 *   they hurt performance): the 3x3 footprint centred under the boss is
 *   scanned through the swept column (old feet -> new feet - 1). If a full
 *   3x3 layer of OBSIDIAN is found, the boss EXPLODES (dies). This is the
 *   intended bug: the boss never learns WHERE the obsidian came from, so
 *   mining the real platform and re-placing it higher up cheeses him;
 * - NEAR DEFENCE: while a player is within NEAR_RADIUS, the boss rolls every
 *   DETONATION_ROLL_INTERVAL ticks and detonates a BIG wind charge explosion
 *   (radius 4, wind-charge knockback, no block damage) that knocks players
 *   away. Under LOW_ALTITUDE_THRESHOLD remaining height the chance rises
 *   from 30% to 50% (the "getting nervous" phase);
 * - FAR ATTACK: when players are present but nobody is near, the boss shoots
 *   authentic BreezeWindCharges (the breeze's own projectile) at the nearest
 *   player for SHOOT_PHASE_TICKS (10 seconds), then stops for a cooldown.
 *   Tagged boss charges explode with 2x the knockback and deal 2x the
 *   damage via CraftmineBreezeBossMixins$BreezeWindChargeMixin (vanilla wind
 *   charge baseline: knockback multiplier 1.22 / 1.0 damage on the direct
 *   hit -> boss charges: 2.44 knockback multiplier, 2.0 damage AoE);
 * - the boss bar is the plumb line: max 50, value = remaining height, WHITE
 *   while high, RED once inside the low-altitude zone;
 * - death = big wind boom + discard + status WON; tickEvents() then
 *   generates the mine exit jigsaw at getPosition() (the platform centre) -
 *   same victory flow as every other boss event.
 *
 * Mobility note: the intended route up is the EXISTING elytra player unlock
 * (PlayerUnlocks: child("elytra", JUMPING_10), givesItemInMine) + wind charge
 * jumps for the smash itself - no extra unlock is added here.
 *
 * Server-side only (MineEvents tick inside the ServerLevel duck). State is
 * codec-serialized through MineEventData like Battle/ElderGuardianBattle, so
 * the fight survives relogs; the boss is re-pinned and the smash progress
 * resumes after a reload.
 */
package dev.craftmine.mines;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class BreezeBossBattle implements MineEvent {
    // ---- entity tags (mixin routing; entityTags() persists with the entity) ----
    public static final String BOSS_TAG = "craftmine_sky_boss";
    public static final String BOSS_CHARGE_TAG = "craftmine_sky_boss_charge";

    // ---- tuning (user spec) ----
    public static final int START_HEIGHT = 50;          // blocks above the platform
    public static final float DESCENT_DAMAGE_STEP = 50.0F;  // every 50 smash damage...
    public static final int DESCENT_BLOCKS = 5;         // ...goes 5 blocks down
    public static final float NEAR_RADIUS = 6.0F;       // "a player is near it"
    public static final float NEAR_DETONATION_CHANCE = 0.30F;  // 30% per roll while near
    public static final float LOW_ALTITUDE_THRESHOLD = 20.0F;  // "if knocked under 20 blocks"
    public static final float LOW_ALTITUDE_CHANCE = 0.50F;     // ...the chance becomes 50
    public static final int DETONATION_ROLL_INTERVAL = 20;     // one roll per second
    public static final float DETONATION_RADIUS = 4.0F;        // "a BIG wind charge explosion"
    public static final int SHOOT_PHASE_TICKS = 200;           // 10 seconds of shooting
    public static final int SHOOT_INTERVAL = 20;               // one charge per second
    public static final int SHOOT_COOLDOWN_TICKS = 100;        // then it stops for 5s (gap-fill)
    public static final float SHOT_SPEED = 1.2F;               // breeze-like, dodgeable
    public static final float BOSS_CHARGE_RADIUS = 3.0F;       // vanilla breeze charge radius
    public static final float BOSS_CHARGE_KNOCKBACK = 2.44F;   // 2x the vanilla 1.22 wind-charge knockback
    public static final float BOSS_CHARGE_DAMAGE = 2.0F;       // 2x the vanilla wind-charge 1.0 direct damage
    public static final float DEATH_BOOM_RADIUS = 6.0F;        // the "touches the ground, explodes" boom

    private final Identifier id;
    private UUID boss;
    private final BlockPos anchor;      // platform centre at surface level (feet reference + exit position)
    private int descentSteps;           // 0..10
    private float smashProgress;        // accumulated (never-dealt) smash damage
    private int rollTimer;              // near-detonation roll cadence
    private int shootTicks;             // remaining volley ticks (0 = not shooting)
    private int shootCooldown;          // post-volley cooldown
    private MineEvent.Status status;

    public static final MapCodec<BreezeBossBattle> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Identifier.CODEC.fieldOf("id").forGetter(battle -> battle.id),
        UUIDUtil.CODEC.fieldOf("boss").forGetter(battle -> battle.boss),
        BlockPos.CODEC.fieldOf("anchor").forGetter(battle -> battle.anchor),
        Codec.INT.fieldOf("descent_steps").forGetter(battle -> battle.descentSteps),
        Codec.FLOAT.fieldOf("smash_progress").forGetter(battle -> battle.smashProgress),
        Codec.INT.fieldOf("roll_timer").forGetter(battle -> battle.rollTimer),
        Codec.INT.fieldOf("shoot_ticks").forGetter(battle -> battle.shootTicks),
        Codec.INT.fieldOf("shoot_cooldown").forGetter(battle -> battle.shootCooldown),
        MineEvent.Status.CODEC.fieldOf("status").forGetter(battle -> battle.status)
    ).apply(instance, BreezeBossBattle::new));

    public BreezeBossBattle(Identifier id, BlockPos anchor) {
        this(id, null, anchor, 0, 0.0F, 0, 0, 0, MineEvent.Status.ACTIVE);
    }

    public BreezeBossBattle(Identifier id, UUID boss, BlockPos anchor, int descentSteps, float smashProgress,
        int rollTimer, int shootTicks, int shootCooldown, MineEvent.Status status) {
        this.id = id;
        this.boss = boss;
        this.anchor = anchor;
        this.descentSteps = descentSteps;
        this.smashProgress = smashProgress;
        this.rollTimer = rollTimer;
        this.shootTicks = shootTicks;
        this.shootCooldown = shootCooldown;
        this.status = status;
    }

    // ---- entry points from the world-effect registration ----

    /**
     * Places the 3x3 obsidian platform flush with the ground at the mine spawn
     * (the ONLY unusual thing about this otherwise completely normal plains
     * mine) and returns the platform surface centre: the boss anchor, the
     * feet-reference height and later the exit jigsaw position. Idempotent:
     * the centre block doubles as the "already built" marker.
     */
    public static BlockPos buildPlatform(ServerLevel serverLevel) {
        BlockPos spawn = serverLevel.getRespawnData().pos();
        int x = spawn.getX();
        int z = spawn.getZ();
        int groundY = serverLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos center = new BlockPos(x, groundY, z);
        if (serverLevel.getBlockState(center.below()).is(Blocks.OBSIDIAN)) {
            return center; // already built (re-entry / reload)
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                serverLevel.setBlock(new BlockPos(x + dx, groundY - 1, z + dz), Blocks.OBSIDIAN.defaultBlockState(), 3);
            }
        }
        return center;
    }

    /**
     * Called by CraftmineBreezeBossMixins$LivingEntityMixin on every vanilla
     * MACE_SMASH damage: routes the hit to the owning event if the target is
     * the sky boss. Regular (non-smash) damage never reaches here - that is
     * the "mace required" part of the spec.
     */
    public static void onBossSmashed(ServerLevel serverLevel, Entity bossEntity, float damage) {
        for (MineEvent mineEvent : ((dev.craftmine.duck.CraftmineLevelDuck)(Object)serverLevel).events()) {
            if (mineEvent instanceof BreezeBossBattle breezeBossBattle && bossEntity.getUUID().equals(breezeBossBattle.boss)) {
                breezeBossBattle.registerSmash(serverLevel, bossEntity, damage);
                return;
            }
        }
    }

    /**
     * Called by CraftmineBreezeBossMixins$BreezeWindChargeMixin instead of the
     * vanilla BreezeWindCharge.explode for boss-tagged charges: 2x the knockback
     * (calculator multiplier 2.44 vs the vanilla 1.22) and a 2.0 damage pass
     * (vanilla wind charges only deal 1.0 on the direct hit). The direct-hit
     * 1.0 already happened in AbstractWindCharge.onHitEntity, so a direct hit
     * totals 3.0 - still "2x the damage" of anything a wind charge normally
     * does per impact.
     */
    public static void explodeBossCharge(AbstractWindCharge charge, Vec3 position) {
        if (!(charge.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity owner = charge.getOwner();
        serverLevel.explode(charge, null, windCalculator(BOSS_CHARGE_KNOCKBACK),
            position.x(), position.y(), position.z(), BOSS_CHARGE_RADIUS, false,
            Level.ExplosionInteraction.TRIGGER, ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_LARGE,
            WeightedList.of(), SoundEvents.BREEZE_WIND_CHARGE_BURST);
        List<Entity> hit = serverLevel.getEntities(charge, charge.getBoundingBox().inflate(BOSS_CHARGE_RADIUS),
            entity -> entity instanceof LivingEntity);
        for (Entity entity : hit) {
            ((LivingEntity)entity).hurtServer(serverLevel,
                serverLevel.damageSources().windCharge(charge, owner instanceof LivingEntity living ? living : null),
                BOSS_CHARGE_DAMAGE);
        }
    }

    /** Vanilla wind-charge damage calculator (no block damage, no entity damage, immune set) with a custom knockback. */
    private static SimpleExplosionDamageCalculator windCalculator(float knockbackMultiplier) {
        return new SimpleExplosionDamageCalculator(true, false, Optional.of(knockbackMultiplier),
            BuiltInRegistries.BLOCK.get(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity()));
    }

    // ---- MineEvent ----

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
            if (serverLevel.isLoaded(this.anchor)) {
                // chunks around the platform are loaded and the boss is gone -> it died (e.g. /kill)
                this.status = MineEvent.Status.WON;
                this.removeBar();
            }
            return;
        }
        if (((LivingEntity)bossEntity).isDeadOrDying()) {
            this.status = MineEvent.Status.WON;
            this.removeBar();
            return;
        }

        this.pinBoss(serverLevel, bossEntity);

        List<ServerPlayer> players = serverLevel.players();
        boolean anyNear = false;
        ServerPlayer nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            double distSq = player.distanceToSqr(bossEntity);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = player;
            }
            if (distSq <= (double)(NEAR_RADIUS * NEAR_RADIUS)) {
                anyNear = true;
            }
        }

        // NEAR: the 30% (50% when low) wind-charge detonation that knocks players away
        if (this.rollTimer > 0) {
            this.rollTimer--;
        }
        if (anyNear && this.rollTimer <= 0) {
            this.rollTimer = DETONATION_ROLL_INTERVAL;
            float chance = this.remainingHeight() < LOW_ALTITUDE_THRESHOLD ? LOW_ALTITUDE_CHANCE : NEAR_DETONATION_CHANCE;
            if (serverLevel.getRandom().nextFloat() < chance) {
                this.detonate(serverLevel, bossEntity);
            }
        }

        // FAR: the 10-second wind charge volley (interrupted instantly by anybody coming near)
        if (this.shootCooldown > 0) {
            this.shootCooldown--;
        } else if (!players.isEmpty() && !anyNear) {
            if (this.shootTicks == 0) {
                this.shootTicks = SHOOT_PHASE_TICKS;
            }
            this.shootTicks--;
            if (nearest != null && this.shootTicks % SHOOT_INTERVAL == 0) {
                this.shootCharge(serverLevel, (Breeze)bossEntity, nearest);
            }
            if (this.shootTicks <= 0) {
                this.shootCooldown = SHOOT_COOLDOWN_TICKS;
            }
        } else {
            this.shootTicks = 0;
        }

        // boss bar = the plumb line (max 50, fills DOWN as the boss is smashed down)
        CustomBossEvent bar = this.ensureBar(serverLevel);
        bar.setName(Component.translatable("world.event.breeze_boss.name"));
        bar.setMax(START_HEIGHT);
        bar.setValue(Math.max(0, this.remainingHeight()));
        bar.setColor(this.remainingHeight() < LOW_ALTITUDE_THRESHOLD ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.WHITE);
        bar.setPlayers(players);
    }

    public int remainingHeight() {
        return START_HEIGHT - this.descentSteps * DESCENT_BLOCKS;
    }

    private void pinBoss(ServerLevel serverLevel, Entity bossEntity) {
        double targetY = (double)this.anchor.getY() + (double)this.remainingHeight();
        if (Math.abs(bossEntity.getX() - ((double)this.anchor.getX() + 0.5)) > 0.1
            || Math.abs(bossEntity.getZ() - ((double)this.anchor.getZ() + 0.5)) > 0.1
            || Math.abs(bossEntity.getY() - targetY) > 0.5) {
            bossEntity.teleportTo((double)this.anchor.getX() + 0.5, targetY, (double)this.anchor.getZ() + 0.5);
        }
    }

    private void spawnBoss(ServerLevel serverLevel) {
        Entity entity = EntityTypes.BREEZE.spawn(serverLevel, this.anchor.above(START_HEIGHT), EntitySpawnReason.EVENT);
        if (entity == null) {
            return; // retry next tick
        }
        ((Mob)entity).setNoAi(true);                    // "a breeze with no ai"
        entity.setPermanentlyInvulnerable(true);        // "so attacks don't do shit"
        ((Mob)entity).setPersistenceRequired();
        entity.addTag(BOSS_TAG);
        this.boss = entity.getUUID();
    }

    /**
     * A landed vanilla smash (MACE_SMASH damage type). The damage is never
     * dealt to the boss - it is converted: every 50 accumulated damage the
     * boss descends 5 blocks, and AFTER the descent the obsidian platform
     * check runs (user spec: after every smash, never per tick).
     */
    private void registerSmash(ServerLevel serverLevel, Entity bossEntity, float damage) {
        if (this.status != MineEvent.Status.ACTIVE) {
            return;
        }
        this.smashProgress += damage;
        while (this.smashProgress >= DESCENT_DAMAGE_STEP && this.status == MineEvent.Status.ACTIVE) {
            this.smashProgress -= DESCENT_DAMAGE_STEP;
            this.descendOneStep(serverLevel, bossEntity);
        }
    }

    private void descendOneStep(ServerLevel serverLevel, Entity bossEntity) {
        double oldY = bossEntity.getY();
        this.descentSteps++;
        double newY = oldY - (double)DESCENT_BLOCKS;
        bossEntity.teleportTo(bossEntity.getX(), newY, bossEntity.getZ());
        serverLevel.sendParticles(ParticleTypes.GUST, bossEntity.getX(), newY + 1.0, bossEntity.getZ(), 6, 0.6, 0.6, 0.6, 0.02);
        serverLevel.playSound(null, bossEntity.getX(), newY, bossEntity.getZ(),
            SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.0F, 0.8F);
        this.checkPlatform(serverLevel, bossEntity, oldY);
    }

    /**
     * The intended bug, kept deliberately dumb: scan the 3x3 footprint
     * centred under the boss through the column just swept by the descent
     * (from the old feet height down to one below the new feet). Any full
     * OBSIDIAN layer = the "ground" = he explodes. The boss never checks
     * WHERE the obsidian came from, so re-placing the mined platform higher
     * up (or under his current feet) finishes him early.
     */
    private void checkPlatform(ServerLevel serverLevel, Entity bossEntity, double oldY) {
        int topY = (int)Math.floor(oldY);
        int bottomY = (int)Math.floor(bossEntity.getY()) - 1;
        int centerX = this.anchor.getX();
        int centerZ = this.anchor.getZ();
        for (int y = bottomY; y <= topY; y++) {
            boolean allObsidian = true;
            for (int dx = -1; dx <= 1 && allObsidian; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!serverLevel.getBlockState(new BlockPos(centerX + dx, y, centerZ + dz)).is(Blocks.OBSIDIAN)) {
                        allObsidian = false;
                        break;
                    }
                }
            }
            if (allObsidian) {
                this.killBoss(serverLevel, bossEntity);
                return;
            }
        }
    }

    private void killBoss(ServerLevel serverLevel, Entity bossEntity) {
        this.status = MineEvent.Status.WON;
        serverLevel.explode(bossEntity, null, windCalculator(2.0F),
            bossEntity.getX(), bossEntity.getY(), bossEntity.getZ(), DEATH_BOOM_RADIUS, false,
            Level.ExplosionInteraction.TRIGGER, ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_LARGE,
            WeightedList.of(), SoundEvents.WIND_CHARGE_BURST);
        bossEntity.discard();
        this.removeBar();
    }

    /** The near defence: a BIG wind charge explosion that knocks players away (no block damage). */
    private void detonate(ServerLevel serverLevel, Entity bossEntity) {
        serverLevel.explode(bossEntity, null, windCalculator(1.22F),
            bossEntity.getX(), bossEntity.getY(), bossEntity.getZ(), DETONATION_RADIUS, false,
            Level.ExplosionInteraction.TRIGGER, ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_LARGE,
            WeightedList.of(), SoundEvents.WIND_CHARGE_BURST);
    }

    /**
     * Shoots the breeze's own projectile (BreezeWindCharge - vanilla radius-3
     * explosion) with the boss as owner, aimed at the nearest player. The
     * charge is tagged so the mixin upgrades its explosion to the 2x variant.
     * The ctor takes its Y from Breeze.getFiringYPosition() which is unset
     * for a NoAI breeze - setPos overrides it explicitly.
     */
    private void shootCharge(ServerLevel serverLevel, Breeze breeze, ServerPlayer target) {
        Vec3 from = breeze.position().add(0.0, 1.2, 0.0);
        Vec3 to = target.position().add(0.0, 1.0, 0.0);
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0e-4) {
            return;
        }
        direction = direction.normalize();
        BreezeWindCharge charge = new BreezeWindCharge(breeze, serverLevel);
        charge.setPos(from.add(direction.scale(0.8)));
        charge.setDeltaMovement(direction.scale((double)SHOT_SPEED));
        charge.addTag(BOSS_CHARGE_TAG);
        serverLevel.addFreshEntity(charge);
    }

    private CustomBossEvent ensureBar(ServerLevel serverLevel) {
        CustomBossEvents events = dev.craftmine.server.CraftmineServerHooks.theGame().getCustomBossEvents();
        CustomBossEvent bar = events.get(this.id);
        if (bar == null) {
            bar = events.create(RandomSource.create(), this.id, Component.translatable("world.event.breeze_boss.name"));
        }
        return bar;
    }

    private void removeBar() {
        CustomBossEvents events = dev.craftmine.server.CraftmineServerHooks.theGame().getCustomBossEvents();
        CustomBossEvent bar = events.get(this.id);
        if (bar != null) {
            // Broadcast to the audience before dropping the entry - see Battle.removeBossBar
            // (26.3's CustomBossEvents.remove does not notify clients; without this the bar
            // stays on the HUD frozen after the breeze fight ends).
            bar.removeAllPlayers();
            events.remove(bar);
        }
    }

    @Override
    public void end(ServerLevel serverLevel, boolean won) {
        this.status = won ? MineEvent.Status.WON : MineEvent.Status.FAILED;
        this.removeBar();
    }

    @Override
    public BlockPos getPosition() {
        return this.anchor; // the exit jigsaw generates here on WON (on the platform)
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

    public BlockPos anchor() {
        return this.anchor;
    }

    public int descentSteps() {
        return this.descentSteps;
    }

    public float smashProgress() {
        return this.smashProgress;
    }

    public MineEvent.Status status() {
        return this.status;
    }
}
