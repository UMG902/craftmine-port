/*
 * Duck: the fork added an isCampfirePlayer flag to the player render state
 * (extracted from YOU_ARE_THE_CAMPFIRE). 26.3 splits player rendering into
 * AvatarRenderState (entity) and PlayerRenderState (first-person, which wraps
 * the avatar state), so one flag here covers both render paths.
 */
package dev.craftmine.duck.client;

public interface CraftmineAvatarRenderStateDuck {
    default boolean craftmine$isCampfirePlayer() {
        return false;
    }

    default void craftmine$setCampfirePlayer(boolean bl) {
    }
}
