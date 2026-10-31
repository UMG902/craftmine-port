package dev.craftmine.mines;

import java.util.ArrayList;
import java.util.List;

/**
 * A named group of world effects with a mutual-exclusion rule, registered in the world
 * effect set registry under a plain string key.
 *
 * <p>An <em>exclusive</em> set (world types, exits, base stone) contributes at most one
 * effect to a crafted mine; a non-exclusive set ("pick at least one biome") may
 * contribute several. Effects join a set through {@code WorldEffect.Builder#inSet} and
 * the set fills up as each effect registers.</p>
 */
public final class WorldEffectSet {

    private final List<WorldEffect> effects = new ArrayList<>();
    private final boolean exclusive;

    public WorldEffectSet(boolean exclusive) {
        this.exclusive = exclusive;
    }

    /** Effects registered into this set so far (filled during bootstrap). */
    public List<WorldEffect> effects() {
        return this.effects;
    }

    /** {@code true} when at most one effect of this set may be active per mine. */
    public boolean exclusive() {
        return this.exclusive;
    }

    /** Called by {@link WorldEffect.Builder} when an effect joining this set registers. */
    void register(WorldEffect effect) {
        this.effects.add(effect);
    }
}
