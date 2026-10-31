/*
 * Craftmine additions to the client level: the mirror of the server's world
 * effect state (unlocked / active effects), synced via
 * ClientboundUpdateUnlockedEffectsPacket and the spawn info.
 * Original: ClientLevel.unlockedEffects / activeEffects fields + accessors.
 */
package dev.craftmine.duck;

import java.util.List;
import dev.craftmine.mines.WorldEffect;

public interface CraftmineClientLevelDuck extends dev.craftmine.duck.CraftmineLevelDuck {
    boolean isEffectUnlocked(WorldEffect worldEffect);

    boolean isActive(WorldEffect worldEffect);

    List<WorldEffect> getActiveEffects();

    List<WorldEffect> getUnlockedEffects();

    void setUnlockedWorldEffects(List<WorldEffect> list);

    /** The level's ACTIVE effects (mine-effects HUD data source; synced via ClientboundUpdateUnlockedEffectsPacket). */
    void setActiveWorldEffects(List<WorldEffect> list);
}
