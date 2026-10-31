package dev.craftmine.mines;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.server.CraftmineServerHooks;

/**
 * A wave-based battle mine event: spawns waves of mobs after configurable countdowns,
 * drives a boss bar (the wave countdown while waiting, the remaining-mobs counter while
 * fighting) and wins once every wave has been cleared. Fully serialized, so a running
 * battle survives a server restart.
 *
 * <p>Composition is declarative: {@link #builder(ServerLevel, String)} assembles
 * {@link Wave}s of {@link SpawnGroup}s (entity type(s), count, spawn strategy) and the
 * boss fight effects in {@link WorldEffects} use it to describe their fights.</p>
 */
public class Battle implements MineEvent {

    private static final int MAX_SPAWN_ATTEMPTS = 500;

    private final Identifier id;
    private final List<Wave> waves;
    private final List<UUID> spawnedMobs;
    private int currentWave;
    private long waveCompletedTick;
    private BlockPos position;
    private MineEvent.Status status;

    public static final MapCodec<Battle> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Identifier.CODEC.fieldOf("id").forGetter(battle -> battle.id),
        Wave.CODEC.listOf().fieldOf("waves").forGetter(battle -> battle.waves),
        UUIDUtil.CODEC.listOf().fieldOf("spawned_mobs").forGetter(battle -> battle.spawnedMobs),
        Codec.INT.fieldOf("current_wave").forGetter(battle -> battle.currentWave),
        Codec.LONG.fieldOf("wave_completed_tick").forGetter(battle -> battle.waveCompletedTick),
        BlockPos.CODEC.fieldOf("position").forGetter(battle -> battle.position),
        MineEvent.Status.CODEC.fieldOf("status").forGetter(battle -> battle.status)
    ).apply(instance, Battle::new));

    public Battle(Identifier id, List<Wave> waves) {
        this(id, waves, List.of(), -1, 0L, BlockPos.ZERO, MineEvent.Status.ACTIVE);
    }

    public Battle(Identifier id, List<Wave> waves, List<UUID> spawnedMobs, int currentWave,
            long waveCompletedTick, BlockPos position, MineEvent.Status status) {
        this.id = id;
        this.waves = waves;
        this.spawnedMobs = new ArrayList<>(spawnedMobs);
        this.currentWave = currentWave;
        this.waveCompletedTick = waveCompletedTick;
        this.position = position;
        this.status = status;
    }

    @Override
    public void tick(ServerLevel level) {
        if (this.getStatus() != MineEvent.Status.ACTIVE) {
            this.removeBossBar();
            return;
        }
        long now = level.getGameTime();
        if (this.currentWave == -1) {
            this.currentWave = 0;
            this.waveCompletedTick = now;
        }
        if (this.waveCompletedTick >= 0L) {
            // waiting for the next wave: run its countdown boss bar, spawn when due
            if (this.currentWave < this.waves.size()) {
                Wave wave = this.waves.get(this.currentWave);
                long waveStartsAt = this.waveCompletedTick + wave.ticksDelay();
                if (waveStartsAt < now) {
                    this.spawnWave(wave, level);
                    this.waveCompletedTick = -1L;
                    this.removeBossBar();
                } else {
                    CustomBossEvent bar = this.getOrCreateBossBar(wave.countdown());
                    bar.setMax(wave.ticksDelay());
                    bar.setValue((int) (now - this.waveCompletedTick));
                    bar.setPlayers(level.players());
                }
            }
        } else {
            // wave active: track the survivors on the boss bar until the field is clear
            List<Entity> alive = this.spawnedMobs.stream()
                .flatMap(uuid -> Optional.ofNullable(level.getEntity(uuid)).filter(Entity::isAlive).stream())
                .toList();
            if (this.currentWave < this.waves.size()) {
                Wave wave = this.waves.get(this.currentWave);
                if (wave.showBar()) {
                    CustomBossEvent bar = this.getOrCreateBossBar(Component.translatable("world.event.remaining"));
                    bar.setMax(this.spawnedMobs.size());
                    bar.setValue(alive.size());
                    bar.setPlayers(level.players());
                }
            }
            if (alive.isEmpty()) {
                this.currentWave++;
                this.waveCompletedTick = now;
            } else {
                this.position = alive.getFirst().blockPosition();
            }
        }
        this.status = this.currentWave < this.waves.size() ? MineEvent.Status.ACTIVE : MineEvent.Status.WON;
    }

    @Override
    public void end(ServerLevel level, boolean won) {
        this.status = won ? MineEvent.Status.WON : MineEvent.Status.FAILED;
        this.currentWave = this.waves.size();
        this.waveCompletedTick = -1L;
        this.removeBossBar();
    }

    @Override
    public BlockPos getPosition() {
        return this.position;
    }

    @Override
    public MineEvent.Status getStatus() {
        return this.status;
    }

    @Override
    public MapCodec<Battle> codec() {
        return CODEC;
    }

    /**
     * Spawns one wave: for every spawn group, rolls a type from the group's holder set
     * and a count, then places each mob. Spawned mobs glow and persist; baby variants
     * (baby zombies wear chainmail helmets, per the snapshot's wave flavour) are opt-in
     * per group.
     */
    public void spawnWave(Wave wave, ServerLevel level) {
        this.spawnedMobs.clear();
        for (SpawnGroup group : wave.groups()) {
            Optional<Holder<EntityType<?>>> type = group.types().getRandomElement(level.getRandom());
            if (type.isEmpty()) {
                continue;
            }
            int count = group.count().sample(level.getRandom());
            for (int i = 0; i < count; i++) {
                BlockPos pos = group.spawnStrategy().getSpawnPos(level);
                if (pos == null || !(type.get().value().spawn(level, pos, EntitySpawnReason.EVENT) instanceof Mob mob)) {
                    continue;
                }
                if (group.useBabyMobs()) {
                    mob.setBaby(true);
                    if (mob instanceof Zombie) {
                        mob.equipItemIfPossible(level, Items.CHAINMAIL_HELMET.getDefaultInstance());
                    }
                }
                mob.setPersistenceRequired();
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1), mob);
                this.spawnedMobs.add(mob.getUUID());
            }
        }
    }

    /** Starts building a battle whose boss bar id is namespaced under the given level. */
    public static Builder builder(ServerLevel level, String name) {
        return new Builder(level.dimension().identifier().withSuffix("/" + name));
    }

    private CustomBossEvent getOrCreateBossBar(Component label) {
        CustomBossEvents events = CraftmineServerHooks.theGame().getCustomBossEvents();
        CustomBossEvent bar = events.get(this.id);
        if (bar == null) {
            bar = events.create(RandomSource.create(), this.id, label);
        }
        bar.setName(label);
        return bar;
    }

    private void removeBossBar() {
        CustomBossEvents events = CraftmineServerHooks.theGame().getCustomBossEvents();
        CustomBossEvent bar = events.get(this.id);
        if (bar != null) {
            // 26.3 API divergence: CustomBossEvents.remove only drops the SavedData entry,
            // it does NOT broadcast the removal to the bar's audience (the snapshot's
            // anh.a(ang) calls ServerBossEvent.removeAll() first; vanilla's /bossbar remove
            // command likewise calls removeAllPlayers() before the manager remove). Without
            // the broadcast the client keeps rendering the bar forever at its last value
            // (full), while the next phase re-creates a fresh bar under the same id with a
            // new packet UUID - the stacked "The Enderman Spawns" + "Mobs Remaining" x2 HUD.
            bar.removeAllPlayers();
            events.remove(bar);
        }
    }

    /** One wave: a list of spawn groups plus countdown/boss-bar presentation. */
    public record Wave(List<SpawnGroup> groups, boolean showBar, Component countdown, int ticksDelay) {

        public static final Codec<Wave> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SpawnGroup.CODEC.listOf().fieldOf("groups").forGetter(Wave::groups),
            Codec.BOOL.fieldOf("show_bar").forGetter(Wave::showBar),
            ComponentSerialization.CODEC.fieldOf("countdown").forGetter(Wave::countdown),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("ticks_delay").forGetter(Wave::ticksDelay)
        ).apply(instance, Wave::new));

        /** Collects spawn groups and presentation before building the wave. */
        public static class Builder {
            private final List<SpawnGroup> groups = new ArrayList<>();
            private boolean showBar = true;
            private Component countdown = Component.translatable("world.event.next_wave");
            private int ticksDelay = 0;

            public Builder spawns(Consumer<SpawnGroup.Builder> configurer) {
                SpawnGroup.Builder builder = new SpawnGroup.Builder();
                configurer.accept(builder);
                this.groups.add(builder.build());
                return this;
            }

            public Builder noBossBar() {
                this.showBar = false;
                return this;
            }

            public Builder countdownLabel(Component label) {
                this.countdown = label;
                return this;
            }

            public Builder ticksDelay(int ticks) {
                this.ticksDelay = ticks;
                return this;
            }

            public Wave build() {
                return new Wave(this.groups, this.showBar, this.countdown, this.ticksDelay);
            }
        }
    }

    /** One spawn roll inside a wave: entity type(s), count and where to place them. */
    public record SpawnGroup(HolderSet<EntityType<?>> types, boolean useBabyMobs, IntProvider count,
            SpawnStrategy spawnStrategy) {

        public static final Codec<SpawnGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryCodecs.holderSet(Registries.ENTITY_TYPE).fieldOf("types").forGetter(SpawnGroup::types),
            Codec.BOOL.fieldOf("useBabyMobs").forGetter(SpawnGroup::useBabyMobs),
            IntProviders.CODEC.fieldOf("count").forGetter(SpawnGroup::count),
            SpawnStrategy.CODEC.fieldOf("spawn_strategy").forGetter(SpawnGroup::spawnStrategy)
        ).apply(instance, SpawnGroup::new));

        /** Collects the group's roster, count and placement before building it. */
        public static class Builder {
            private HolderSet<EntityType<?>> types = HolderSet.empty();
            private boolean useBabyMobs = false;
            private IntProvider count = ConstantInt.of(1);
            private SpawnStrategy spawnStrategy = new SpawnStrategy(SpawnType.NEAR_PLAYER, BlockPos.ZERO, 40, false);

            public Builder types(HolderSet<EntityType<?>> types) {
                this.types = types;
                return this;
            }

            public Builder type(EntityType<?> type) {
                return this.types(HolderSet.direct(type.builtInRegistryHolder()));
            }

            public Builder useBabyMobs(boolean useBabyMobs) {
                this.useBabyMobs = useBabyMobs;
                return this;
            }

            public Builder count(IntProvider count) {
                this.count = count;
                return this;
            }

            public Builder count(int count) {
                return this.count(ConstantInt.of(count));
            }

            public Builder withSpawnStrategy(Consumer<SpawnStrategy.Builder> configurer) {
                SpawnStrategy.Builder builder = new SpawnStrategy.Builder();
                configurer.accept(builder);
                this.spawnStrategy = builder.build();
                return this;
            }

            public SpawnGroup build() {
                return new SpawnGroup(this.types, this.useBabyMobs, this.count, this.spawnStrategy);
            }
        }
    }

    /**
     * Placement rule for a spawned mob. Serialized by name through {@link SpawnType};
     * position-based strategies search up to {@link Battle#MAX_SPAWN_ATTEMPTS} offsets
     * for a spot with a sturdy floor and 2-block headroom.
     */
    public record SpawnStrategy(SpawnType type, BlockPos offset, int range, boolean allowInAir) {

        public static final Codec<SpawnStrategy> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SpawnType.CODEC.fieldOf("type").forGetter(SpawnStrategy::type),
            BlockPos.CODEC.fieldOf("offset").forGetter(SpawnStrategy::offset),
            ExtraCodecs.POSITIVE_INT.fieldOf("range").forGetter(SpawnStrategy::range),
            Codec.BOOL.fieldOf("allow_in_air").forGetter(SpawnStrategy::allowInAir)
        ).apply(instance, SpawnStrategy::new));

        @Nullable
        public BlockPos getSpawnPos(ServerLevel level) {
            return switch (this.type) {
                case FIXED_POSITION -> this.offset;
                case ON_HEIGHTMAP -> this.offset.atY(level.getHeight(Heightmap.Types.WORLD_SURFACE, this.offset) + 1);
                case NEAR_POSITION -> this.positionNear(level, this.offset);
                case NEAR_PLAYER -> this.positionNearPlayer(level);
                case WARDEN_ARENA -> ((CraftmineLevelDuck) (Object) level).craftmine$wardenArenaPos().offset(5, 2, -1);
            };
        }

        @Nullable
        private BlockPos positionNear(ServerLevel level, BlockPos anchor) {
            RandomSource random = level.getRandom();
            for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
                BlockPos candidate = anchor.offset(random.nextIntBetweenInclusive(-this.range, this.range), 0,
                    random.nextIntBetweenInclusive(-this.range, this.range));
                if (this.allowInAir) {
                    if (isOkSpawnSpace(level, candidate)) {
                        return candidate;
                    }
                    continue;
                }
                BlockPos.MutableBlockPos cursor = candidate.mutable();
                while (isEmpty(level, cursor) && Math.abs(cursor.getY() - anchor.getY()) < this.range) {
                    cursor.move(Direction.DOWN);
                }
                cursor.move(Direction.UP);
                while (!isEmpty(level, cursor) && Math.abs(cursor.getY() - anchor.getY()) < this.range) {
                    cursor.move(Direction.UP);
                }
                if (isEmpty(level, cursor.below()) || !isOkSpawnSpace(level, cursor)) {
                    continue;
                }
                return cursor.immutable();
            }
            return null;
        }

        private static boolean isEmpty(ServerLevel level, BlockPos pos) {
            return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        }

        private static boolean isOkSpawnSpace(ServerLevel level, BlockPos pos) {
            for (BlockPos around : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))) {
                if (!isEmpty(level, around)) {
                    return false;
                }
            }
            return true;
        }

        @Nullable
        private BlockPos positionNearPlayer(ServerLevel level) {
            List<ServerPlayer> players = level.getPlayers(player -> player.isAlive() && !player.isSpectator());
            if (players.isEmpty()) {
                return this.positionNear(level, this.offset);
            }
            return this.positionNear(level, Util.getRandom(players, level.getRandom()).blockPosition().offset(this.offset));
        }

        /** Collects type, offset, range and air permission before building the strategy. */
        public static class Builder {
            private SpawnType type = SpawnType.NEAR_PLAYER;
            private BlockPos offset = BlockPos.ZERO;
            private int range = 20;
            private boolean allowInAir = false;

            public Builder type(SpawnType type) {
                this.type = type;
                return this;
            }

            public Builder offset(BlockPos offset) {
                this.offset = offset;
                return this;
            }

            public Builder range(int range) {
                this.range = range;
                return this;
            }

            /** Permits spawns in mid-air (skips the floor search). */
            public Builder allowInAir() {
                this.allowInAir = true;
                return this;
            }

            public SpawnStrategy build() {
                return new SpawnStrategy(this.type, this.offset, this.range, this.allowInAir);
            }
        }
    }

    /** How a spawn position is derived; serialized by name. */
    public enum SpawnType implements StringRepresentable {
        /** Always the exact configured offset. */
        FIXED_POSITION("fixed_position"),
        /** The offset, snapped to the world surface plus one. */
        ON_HEIGHTMAP("on_heightmap"),
        /** A random spot within the configured range of the offset. */
        NEAR_POSITION("near_position"),
        /** A random spot near a random living player (offset applied on top). */
        NEAR_PLAYER("near_player"),
        /** The warden arena anchor used by the warden boss fight. */
        WARDEN_ARENA("warden_arena");

        public static final Codec<SpawnType> CODEC = StringRepresentable.fromEnum(SpawnType::values);

        private final String id;

        SpawnType(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

    /** Collects waves before building the battle. */
    public static class Builder {
        private final Identifier id;
        private final List<Wave> waves = new ArrayList<>();

        public Builder(Identifier id) {
            this.id = id;
        }

        public Builder withWave(Consumer<Wave.Builder> configurer) {
            Wave.Builder builder = new Wave.Builder();
            configurer.accept(builder);
            this.waves.add(builder.build());
            return this;
        }

        public Battle build() {
            return new Battle(this.id, this.waves);
        }
    }
}
