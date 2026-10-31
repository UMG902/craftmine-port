package dev.craftmine.mines;

/**
 * How a {@link WorldEffect} becomes available to the player.
 *
 * <p>{@link #UNLOCKED_ON_WIN} is the classic loop (win a mine while the effect is
 * active and it becomes permanently unlockable); {@link #UNLOCKED_BY_CONDITION}
 * attaches explicit unlock conditions instead. Effects that are never unlocked exist
 * purely as wiring for special mines and boss fights.</p>
 */
public enum UnlockMode {
    /** Can never be unlocked; hidden from the unlock tree. */
    NEVER_UNLOCKED,
    /** Available to every player from the start. */
    ALWAYS_UNLOCKED,
    /** Unlocked through explicit conditions attached to the effect. */
    UNLOCKED_BY_CONDITION,
    /** Unlocked by winning a mine while the effect is active. */
    UNLOCKED_ON_WIN
}
