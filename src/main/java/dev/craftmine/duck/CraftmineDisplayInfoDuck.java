/*
 * The craftmine fork stored unlock-tree layout positions on DisplayInfo
 * (mutable x/y). 26.3 DisplayInfo is a frozen record, so the state lives in
 * this duck, implemented by DisplayInfoMixin.
 */
package dev.craftmine.duck;

public interface CraftmineDisplayInfoDuck {
    float craftmine$getX();

    float craftmine$getY();

    void craftmine$setLocation(float x, float y);
}
