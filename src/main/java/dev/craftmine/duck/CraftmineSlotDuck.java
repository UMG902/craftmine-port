package dev.craftmine.duck;

/**
 * Craftmine repositions container slots at runtime (mine-crafting spiral layout).
 * 26.3 made Slot.x/y final, so a mixin with @Mutable provides this setter.
 */
public interface CraftmineSlotDuck {
    default void craftmine$setPosition(int x, int y) {
        throw new UnsupportedOperationException("implemented by SlotMixin");
    }

    /** craftmine's Slot.shouldMove (mine-crafting spiral slots). */
    default boolean shouldMove() {
        return false;
    }

    /** craftmine's Slot.shouldRotate. */
    default boolean shouldRotate() {
        return false;
    }

    /** craftmine's Slot animation target (spiral layout). */
    default void craftmine$setAnimTarget(float x, float y) {
    }

    default float craftmine$animTargetX() {
        return this instanceof net.minecraft.world.inventory.Slot slot ? slot.x : 0.0f;
    }

    default float craftmine$animTargetY() {
        return this instanceof net.minecraft.world.inventory.Slot slot ? slot.y : 0.0f;
    }
}
