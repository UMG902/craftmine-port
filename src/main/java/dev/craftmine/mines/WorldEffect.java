package dev.craftmine.mines;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import dev.craftmine.unlock.UnlockCondition;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import dev.craftmine.duck.CraftmineDuckBridge;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.registry.CraftmineRegistries;
import dev.craftmine.server.CraftmineServerHooks;

/**
 * One toggleable property of a mine: a world type, a biome selection, a mob set, a
 * challenge or a boss-fight wiring. Effects are records registered into the world
 * effect registry by key (both codecs resolve through the registry); their gameplay
 * hooks are small consumers fired by the level mixin on mine enter/leave/tick.
 *
 * <p>Effects are built exclusively through {@link Builder}, which assembles the
 * translatable name/description/hint triple, the optional item-model icon, the
 * unlock gating ({@link UnlockMode} + conditions) and the randomization policy
 * ({@link RandomizationMode}) used by the Mine Crafter.</p>
 */
public record WorldEffect(
    String key,
    Component name,
    Component description,
    Component unlockHint,
    @Nullable Identifier itemModel,
    List<WorldEffectComponent> components,
    UnlockMode unlockMode,
    List<UnlockCondition> unlockedBy,
    List<WorldEffect> unlockedAfter,
    int requiredUnlockCount,
    Set<WorldEffectSet> inSets,
    Set<WorldEffect> incompatibleWith,
    float experienceModifier,
    int randomWeight,
    RandomizationMode randomizationMode,
    boolean multiplayerOnly,
    Consumer<ServerLevel> onMineEnter,
    Consumer<ServerLevel> onMineLeave,
    Consumer<ServerLevel> onMineTick
) {

    public static final Codec<WorldEffect> CODEC = CraftmineRegistries.WORLD_EFFECT.byNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldEffect> STREAM_CODEC =
        ByteBufCodecs.registry(CraftmineRegistries.WORLD_EFFECT_KEY);

    /** {@code true} when this effect does not clash with anything already chosen. */
    public boolean isValidWith(Collection<WorldEffect> chosen) {
        return chosen.stream().noneMatch(effect -> areIncompatible(effect, this));
    }

    public static boolean areIncompatible(WorldEffect a, WorldEffect b) {
        return a.incompatibleWith.contains(b) || b.incompatibleWith.contains(a);
    }

    /**
     * {@code true} when the player may unlock this effect now: it must be usable, and
     * when it demands {@link #requiredUnlockCount} prior unlocks those must all be met
     * (counted over the world-effect registry in registration order).
     */
    public boolean canUnlock(ServerLevel level) {
        if (!this.canUse(level)) {
            return false;
        }
        if (this.requiredUnlockCount == 0) {
            return true;
        }
        long unlocked = CraftmineRegistries.WORLD_EFFECT.stream()
            .filter(CraftmineDuckBridge.level(level)::isEffectUnlocked)
            .limit(this.requiredUnlockCount)
            .count();
        return unlocked == (long) this.requiredUnlockCount;
    }

    public boolean canUse(ServerLevel level) {
        // Task 29 (BugMine allowSoulLinkOnLanServers, always-on): the fork gated
        // multiplayer-only effects on isSingleplayer(), which stays true even when a
        // singleplayer world is opened to LAN. Gate on !isPublished() instead -
        // a private singleplayer world (multiplayerScope == OFF) stays locked,
        // snapshot-faithful; a LAN-open world or a dedicated server passes.
        if (this.multiplayerOnly() && !CraftmineServerHooks.theGame().server().isPublished()) {
            return false;
        }
        return this.unlockMode != UnlockMode.NEVER_UNLOCKED;
    }

    /**
     * {@code true} when the Mine Crafter may roll this effect as a random ingredient.
     * Always-available starter effects (surface world, plains, sheep, ...) randomize
     * even before anything is unlocked — without that, the must-have set picker would
     * hit an empty candidate list on a fresh hub and the Mine Crafter GUI could never
     * open.
     */
    public boolean canRandomize(ServerLevel level) {
        // Task 29 (BugMine allowSoulLinkOnLanServers, always-on): see canUse -
        // !isPublished() instead of isSingleplayer(), so LAN worlds may roll
        // multiplayer-only effects as random ingredients like dedicated servers do.
        if (this.multiplayerOnly() && !CraftmineServerHooks.theGame().server().isPublished()) {
            return false;
        }
        if (this.randomizationMode == RandomizationMode.NEVER) {
            return false;
        }
        if (this.unlockMode == UnlockMode.ALWAYS_UNLOCKED) {
            return true;
        }
        if (((CraftmineLevelDuck) (Object) level).isEffectUnlocked(this)) {
            return true;
        }
        return this.randomizationMode == RandomizationMode.WHEN_UNLOCKABLE && this.canUnlock(level);
    }

    public static Builder builder(String key) {
        return new Builder(key);
    }

    /** Streams the attached components that are instances of the given type. */
    public <T extends WorldEffectComponent> Stream<T> componentsOfType(Class<T> type) {
        return this.components.stream()
            .filter(component -> type.isAssignableFrom(component.getClass()))
            .map(type::cast);
    }

    /** Combines hook consumers into one; empty lists collapse to a no-op. */
    private static Consumer<ServerLevel> combineHooks(List<Consumer<ServerLevel>> hooks) {
        if (hooks.isEmpty()) {
            return level -> {};
        }
        List<Consumer<ServerLevel>> snapshot = List.copyOf(hooks);
        return level -> {
            for (Consumer<ServerLevel> hook : snapshot) {
                hook.accept(level);
            }
        };
    }

    /**
     * Collects name/description style, icon, components, unlock gating, randomization
     * policy and the enter/leave/tick hooks before registering the effect.
     */
    public static class Builder {
        private final String key;
        private Style nameStyle = Style.EMPTY.withColor(ChatFormatting.BLUE);
        @Nullable
        private Identifier itemModel;
        private final List<WorldEffectComponent> components = new ArrayList<>();
        private final List<UnlockCondition> unlockedBy = new ArrayList<>();
        private final List<WorldEffect> unlockedAfter = new ArrayList<>();
        private int requiredUnlockCount;
        private final Set<WorldEffectSet> inSets = new ObjectArraySet<>();
        private float xpModifier = 1.0F;
        private int randomWeight = 100;
        private RandomizationMode randomizationMode = RandomizationMode.WHEN_UNLOCKED;
        private UnlockMode unlockMode = UnlockMode.UNLOCKED_ON_WIN;
        private boolean multiplayerOnly = false;
        private final List<Consumer<ServerLevel>> onMineEnter = new ArrayList<>();
        private final List<Consumer<ServerLevel>> onMineLeave = new ArrayList<>();
        private final List<Consumer<ServerLevel>> onMineTick = new ArrayList<>();
        private final Set<WorldEffect> incompatibleWith = new ObjectArraySet<>();

        public Builder(String key) {
            this.key = key;
        }

        public Builder withNameStyle(Style style) {
            this.nameStyle = style;
            return this;
        }

        private Builder withItemModel(Identifier model) {
            this.itemModel = model;
            return this;
        }

        public Builder withItemModelOf(Item item) {
            // 26.3 binds item components lazily (after datapack load), so reading
            // DataComponents.ITEM_MODEL during bootstrap throws. The bound value is
            // Properties.effectiveModel(), which defaults to the item's registry id
            // (ResourceKey::identifier) — exactly what every call site relies on.
            return this.withItemModel(BuiltInRegistries.ITEM.getKey(item));
        }

        public Builder withCustomIcon(String path) {
            return this.withItemModel(CustomIcons.register(path));
        }

        public Builder withRandomizationWeight(int weight) {
            this.randomWeight = weight;
            return this;
        }

        public Builder alwaysRandomizable() {
            this.randomizationMode = RandomizationMode.WHEN_UNLOCKABLE;
            return this;
        }

        public Builder notRandomizable() {
            this.randomWeight = 0;
            this.randomizationMode = RandomizationMode.NEVER;
            return this;
        }

        public Builder neverUnlocked() {
            this.randomWeight = 0;
            this.randomizationMode = RandomizationMode.NEVER;
            this.unlockMode = UnlockMode.NEVER_UNLOCKED;
            return this;
        }

        public Builder multiplayerOnly() {
            this.multiplayerOnly = true;
            return this;
        }

        public Builder addComponent(WorldEffectComponent... componentArray) {
            this.components.addAll(List.of(componentArray));
            return this;
        }

        @SafeVarargs
        public final Builder addBiomes(ResourceKey<Biome>... biomeKeys) {
            for (ResourceKey<Biome> biomeKey : biomeKeys) {
                this.components.add(new WorldGenEffect.AddBiome(biomeKey));
            }
            return this;
        }

        public final Builder modifyingWorldGen(Consumer<WorldGenBuilder> configurer) {
            this.components.add((WorldGenEffect) configurer::accept);
            return this;
        }

        public Builder unlockedBy(UnlockCondition... conditions) {
            this.unlockedBy.addAll(List.of(conditions));
            this.unlockMode = UnlockMode.UNLOCKED_BY_CONDITION;
            return this;
        }

        public Builder unlockedByWinning() {
            this.unlockMode = UnlockMode.UNLOCKED_ON_WIN;
            return this;
        }

        public Builder unlockedByDefault() {
            this.unlockMode = UnlockMode.ALWAYS_UNLOCKED;
            return this;
        }

        public Builder xpModifier(float modifier) {
            this.xpModifier = modifier;
            return this;
        }

        public Builder unlockedAfter(WorldEffect... effects) {
            this.unlockedAfter.addAll(List.of(effects));
            return this;
        }

        public Builder requiresUnlockCount(int count) {
            this.requiredUnlockCount = count;
            return this;
        }

        public Builder inSet(WorldEffectSet set) {
            this.inSets.add(set);
            return this;
        }

        public Builder incompatibleWith(WorldEffect... effects) {
            this.incompatibleWith.addAll(List.of(effects));
            return this;
        }

        public Builder onMineEnter(Consumer<ServerLevel> hook) {
            this.onMineEnter.add(hook);
            return this;
        }

        public Builder onMineLeave(Consumer<ServerLevel> hook) {
            this.onMineLeave.add(hook);
            return this;
        }

        /** Convenience: fires a per-player hook for every player in the mine. */
        public Builder onPlayerMineEnter(Consumer<ServerPlayer> hook) {
            this.onMineEnter.add(level -> level.players().forEach(hook));
            return this;
        }

        public Builder onMineTick(Consumer<ServerLevel> hook) {
            this.onMineTick.add(hook);
            return this;
        }

        public WorldEffect register() {
            return Registry.register(CraftmineRegistries.WORLD_EFFECT, Identifier.withDefaultNamespace(this.key), this.build());
        }

        private WorldEffect build() {
            MutableComponent name = Component.translatable("world.effect." + this.key + ".name").withStyle(this.nameStyle);
            MutableComponent description = Component.translatable("world.effect." + this.key + ".description");
            MutableComponent hint = Component.translatable("world.effect." + this.key + ".hint");
            WorldEffect effect = new WorldEffect(this.key, name, description, hint, this.itemModel, this.components,
                this.unlockMode, this.unlockedBy, this.unlockedAfter, this.requiredUnlockCount, this.inSets,
                this.incompatibleWith, this.xpModifier, this.randomWeight, this.randomizationMode,
                this.multiplayerOnly, combineHooks(this.onMineEnter), combineHooks(this.onMineLeave),
                combineHooks(this.onMineTick));
            this.inSets.forEach(set -> set.register(effect));
            return effect;
        }
    }
}
