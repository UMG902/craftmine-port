package dev.craftmine.mines;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.resources.Identifier;

/**
 * Registry of custom sprite icons used as ingredient item models.
 *
 * <p>World effects and unlocks that do not map to a real item display a custom icon
 * texture instead; the identifier is registered here so model lookups can resolve it.
 * Registration happens at bootstrap time on both physical sides.</p>
 */
public final class CustomIcons {

    private static final Set<Identifier> ICONS = new HashSet<>();

    private CustomIcons() {
    }

    /** Registers {@code minecraft:<path>} as a custom icon and returns its identifier. */
    public static Identifier register(String path) {
        Identifier id = Identifier.withDefaultNamespace(path);
        ICONS.add(id);
        return id;
    }

    /** Read-only view of every custom icon registered so far. */
    public static Set<Identifier> icons() {
        return Collections.unmodifiableSet(ICONS);
    }
}
