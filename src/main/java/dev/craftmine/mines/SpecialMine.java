package dev.craftmine.mines;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import dev.craftmine.inventory.MineCraftingMenu;
import dev.craftmine.unlock.UnlockCondition;
import dev.craftmine.registry.CraftmineRegistries;

/**
 * A hand-crafted "special mine" recipe: a fixed set of required world effects, optional
 * one-of groups (usually a biome), and unlock gating. Registered into the special-mine
 * registry by key; both codecs resolve through that registry, so only the key is ever
 * persisted or synced.
 */
public record SpecialMine(
    String key,
    Component name,
    Component description,
    List<WorldEffect> requiredEffects,
    List<List<WorldEffect>> randomEffects,
    List<UnlockCondition> unlockedBy,
    List<SpecialMine> unlockedAfter,
    int extraRandom
) {

    public static final Codec<SpecialMine> CODEC = CraftmineRegistries.SPECIAL_MINE.byNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, SpecialMine> STREAM_CODEC =
        ByteBufCodecs.registry(CraftmineRegistries.SPECIAL_MINE_KEY);

    public static Builder builder(String key) {
        return new Builder(key);
    }

    /**
     * Rolls the concrete effect list for one mine instance: the required effects, one
     * valid pick from every one-of group (re-rolling inside the group while candidates
     * clash with what is already chosen), plus {@link #extraRandom} free picks.
     */
    public List<WorldEffect> instantiate(ServerLevel level) {
        RandomSource random = level.getRandom();
        List<WorldEffect> chosen = new ArrayList<>(this.requiredEffects);
        for (List<WorldEffect> candidates : this.randomEffects) {
            List<WorldEffect> pool = new ArrayList<>(candidates);
            WorldEffect picked = null;
            while (picked == null && !pool.isEmpty()) {
                picked = Util.getRandom(pool, random);
                if (!picked.isValidWith(chosen)) {
                    pool.remove(picked);
                    picked = null;
                }
            }
            if (picked != null) {
                chosen.add(picked);
            }
        }
        for (int i = 0; i < this.extraRandom; i++) {
            MineCraftingMenu.getRandomEffect(level, chosen, Set.of()).ifPresent(chosen::add);
        }
        return chosen;
    }

    /**
     * Collects the effect recipe and unlock gating before registration. New mines
     * without an unlock condition default to always available; anything with an
     * {@code unlockedAfter} prerequisite must name an explicit condition.
     */
    public static class Builder {
        private final String key;
        private Style nameStyle = Style.EMPTY.withColor(ChatFormatting.BLUE);
        private final List<WorldEffect> requiredEffects = new ArrayList<>();
        private final List<List<WorldEffect>> randomEffects = new ArrayList<>();
        private final List<UnlockCondition> unlockedBy = new ArrayList<>();
        private final List<SpecialMine> unlockedAfter = new ArrayList<>();
        private int extraRandom = 0;

        public Builder(String key) {
            this.key = key;
        }

        public Builder withNameStyle(Style style) {
            this.nameStyle = style;
            return this;
        }

        public Builder withRequiredEffects(WorldEffect... effects) {
            this.requiredEffects.addAll(List.of(effects));
            return this;
        }

        /** Adds a one-of group: exactly one of these effects is rolled into the mine. */
        public Builder withOneOf(WorldEffect... effects) {
            this.randomEffects.add(List.of(effects));
            return this;
        }

        public Builder withOneOf(WorldEffectSet effectSet) {
            this.randomEffects.add(effectSet.effects());
            return this;
        }

        public Builder withRandomEffects(int count) {
            this.extraRandom = count;
            return this;
        }

        public Builder addUnlockedBy(UnlockCondition... conditions) {
            this.unlockedBy.addAll(List.of(conditions));
            return this;
        }

        public Builder unlockedAfter(SpecialMine... mines) {
            this.unlockedAfter.addAll(List.of(mines));
            return this;
        }

        /** Builds and registers the special mine under its key. */
        public SpecialMine register() {
            SpecialMine mine = this.build();
            if (mine.unlockedBy().isEmpty()) {
                if (!this.unlockedAfter.isEmpty()) {
                    throw new IllegalStateException("Missing unlock condition for special mine " + this.key);
                }
                SpecialMines.DEFAULT_UNLOCKED.add(mine);
            }
            return Registry.register(CraftmineRegistries.SPECIAL_MINE, mine.key(), mine);
        }

        private SpecialMine build() {
            MutableComponent name = Component.translatable("mine." + this.key + ".name").withStyle(this.nameStyle);
            MutableComponent description = Component.translatable("mine." + this.key + ".description");
            return new SpecialMine(this.key, name, description, this.requiredEffects, this.randomEffects,
                this.unlockedBy, this.unlockedAfter, this.extraRandom);
        }
    }
}
