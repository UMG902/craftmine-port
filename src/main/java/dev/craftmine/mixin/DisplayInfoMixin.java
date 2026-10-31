/*
 * Craftmine additions to DisplayInfo: unlock-tree layout position.
 * The fork made these fields mutable on DisplayInfo; 26.3 DisplayInfo is a
 * record, so the state lives here (matches the original defaults of 0).
 */
package dev.craftmine.mixin;

import net.minecraft.advancements.DisplayInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.craftmine.duck.CraftmineDisplayInfoDuck;

@Mixin(DisplayInfo.class)
public abstract class DisplayInfoMixin implements CraftmineDisplayInfoDuck {
    @Unique
    private float craftmine$x;
    @Unique
    private float craftmine$y;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$initFields(CallbackInfo ci) {
        // Mixin does not merge field initializers on this class-file version.
    }

    @Override
    public float craftmine$getX() {
        return this.craftmine$x;
    }

    @Override
    public float craftmine$getY() {
        return this.craftmine$y;
    }

    @Override
    public void craftmine$setLocation(float x, float y) {
        this.craftmine$x = x;
        this.craftmine$y = y;
    }
}
