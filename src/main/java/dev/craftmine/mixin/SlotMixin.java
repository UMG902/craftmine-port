/*
 * Slot extras shared by the mine crafter UI (slot orbit animation targets)
 * and the unlock-gating system (repositioning the inventory crafting grid
 * for the 3x3 layout; 26.3 declares Slot.x/y final - @Mutable shadow).
 */
package dev.craftmine.mixin;

import dev.craftmine.duck.CraftmineSlotDuck;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Slot.class)
public abstract class SlotMixin
implements CraftmineSlotDuck {
    @Mutable
    @Final
    @Shadow
    public int x;
    @Mutable
    @Final
    @Shadow
    public int y;
    @Unique
    private float craftmine$animTargetX;
    @Unique
    private float craftmine$animTargetY;

    @Unique
    public void craftmine$setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Unique
    public void craftmine$setAnimTarget(float x, float y) {
        this.craftmine$animTargetX = x;
        this.craftmine$animTargetY = y;
    }

    @Unique
    public float craftmine$animTargetX() {
        return this.craftmine$animTargetX;
    }

    @Unique
    public float craftmine$animTargetY() {
        return this.craftmine$animTargetY;
    }
}
