/**
 * The heart of the Craftmine port: a <em>mine</em> is a pocket dimension rolled by the
 * Mine Crafter, shaped by {@link dev.craftmine.mines.WorldEffect world effects} (world
 * types, biomes, mob sets, challenges) and optionally hosting a
 * {@link dev.craftmine.mines.MineEvent mine event} such as a boss battle or a raid.
 *
 * <p>The mine's runtime state machine (rolling, entering, leaving, winning, failing)
 * lives in {@link dev.craftmine.mines.MineData} (persisted per level) and the server
 * level mixin that drives it. Unlock gating is expressed through
 * {@link dev.craftmine.mines.UnlockMode} plus the shared unlock conditions, and
 * randomization through {@link dev.craftmine.mines.RandomizationMode}.</p>
 *
 * <p>Every class in this package is the mod's own implementation of the snapshot's
 * mine system; special-mine recipes and world effect definitions register through
 * {@code dev.craftmine.registry.CraftmineRegistries} at bootstrap time.</p>
 */
package dev.craftmine.mines;
