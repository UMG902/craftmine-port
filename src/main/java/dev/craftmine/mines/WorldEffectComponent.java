package dev.craftmine.mines;

/**
 * Marker interface for pluggable behaviour attached to a {@link WorldEffect} through
 * {@code WorldEffect.Builder#addComponent}. The primary implementation is
 * {@link WorldGenEffect}, applied to the mine's world-generation builder while the
 * mine is crafted.
 *
 * <p>Query components with {@link WorldEffect#componentsOfType(Class)} or
 * {@link WorldEffects#componentsOfType(List, Class)}.</p>
 */
public interface WorldEffectComponent {
}
