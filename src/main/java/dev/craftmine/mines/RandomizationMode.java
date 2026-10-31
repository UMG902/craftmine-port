package dev.craftmine.mines;

/**
 * Controls whether a {@link WorldEffect} may be rolled as a random ingredient by the
 * Mine Crafter, and under which unlock state that is allowed.
 */
public enum RandomizationMode {
    /** Never appears as a random ingredient (boss fights, special-mine-only effects). */
    NEVER,
    /** May be rolled once it is unlockable, even before the player unlocked it. */
    WHEN_UNLOCKABLE,
    /** May only be rolled after the player has unlocked it. */
    WHEN_UNLOCKED
}
