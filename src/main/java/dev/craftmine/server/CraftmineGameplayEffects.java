package dev.craftmine.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import dev.craftmine.mines.WorldEffect;
import dev.craftmine.mines.WorldEffects;

/**
 * FT-8 gameplay layer: query helpers for world effects that the fork read
 * through its own Level.getIsIcy()/getIsBouncy() accessors and inline
 * isActive(...) checks inside vanilla classes.
 *
 * The port's world-effect state lives on the CraftmineLevelDuck (implemented
 * by ServerLevelMixin on the server, ClientLevelMixin on the client), so all
 * helpers route through a duck cast and answer false on non-mine levels
 * (plain vanilla worlds never implement the duck) - matching the fork, where
 * isActive only reports true inside mines.
 */
public final class CraftmineGameplayEffects {
    private CraftmineGameplayEffects() {
    }

    /** Fork: Level.getIsIcy() -> isActive(WorldEffects.ICY). */
    public static boolean isIcy(Level level) {
        return level instanceof dev.craftmine.duck.CraftmineLevelDuck duck
            && duck.isActive(WorldEffects.ICY);
    }

    /** Fork: Level.getIsBouncy() -> isActive(WorldEffects.BOUNCY). */
    public static boolean isBouncy(Level level) {
        return level instanceof dev.craftmine.duck.CraftmineLevelDuck duck
            && duck.isActive(WorldEffects.BOUNCY);
    }

    /** Generic duck dispatch for the remaining effect checks. */
    public static boolean isActive(Level level, WorldEffect effect) {
        return level instanceof dev.craftmine.duck.CraftmineLevelDuck duck
            && duck.isActive(effect);
    }

    /** Convenience overload used from entity mixins (entity.level()). */
    public static boolean isIcy(Entity entity) {
        return isIcy(entity.level());
    }

    public static boolean isBouncy(Entity entity) {
        return isBouncy(entity.level());
    }
}
