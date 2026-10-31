/*
 * Duck interface for craftmine additions to Warden (the acting boss-fight sequence).
 * Implemented via WardenMixin.
 */
package dev.craftmine.duck;

import net.minecraft.world.entity.monster.warden.Warden;

public interface CraftmineWardenDuck {
    void stopActing();

    int craftmine$crowdWaitingSoundTimeLeft();

    void craftmine$setCrowdWaitingSoundTimeLeft(int value);

    int craftmine$timeUntilGoathornHint();

    void craftmine$setTimeUntilGoathornHint(int value);

    int craftmine$timeUntilDiscShouldPlay();

    void craftmine$setTimeUntilDiscShouldPlay(int value);

    int craftmine$timeUntilLightShouldTurnOn();

    void craftmine$setTimeUntilLightShouldTurnOn(int value);

    int craftmine$timeUntilMobHead();

    void craftmine$setTimeUntilMobHead(int value);

    int craftmine$timeUntilRockets();

    void craftmine$setTimeUntilRockets(int value);

    static CraftmineWardenDuck of(Warden warden) {
        return (CraftmineWardenDuck) (Object) warden;
    }
}
