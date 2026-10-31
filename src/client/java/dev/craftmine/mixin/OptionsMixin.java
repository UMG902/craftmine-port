package dev.craftmine.mixin;

import java.lang.reflect.Field;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.craftmine.duck.CraftmineOptionsDuck;

/**
 * Registers the Player Unlocks key mapping (default U, like the fork's
 * Options.keyUnlocks) so it shows up in Options > Controls > Key Binds and
 * is fully rebindable, and so Options.save/load persists it.
 *
 * 26.3 keeps keyMappings as a public final array literal assembled in the
 * constructor - an entry cannot be appended in place, so after <init> the
 * field is replaced with an extended copy. Safe: nothing captures the array
 * before construction finishes, and every consumer re-reads the field.
 * Replacing an instance final field via reflection is allowed
 * (only static-final and record fields are rejected).
 */
@Mixin(Options.class)
public abstract class OptionsMixin implements CraftmineOptionsDuck {

    @Unique
    private KeyMapping craftmine$keyUnlocks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$registerKeyUnlocks(net.minecraft.client.Minecraft minecraft, java.io.File file, CallbackInfo ci) {
        // 26.3 keycodes are SDL SCANCODES (GLFW is gone from the windowing
        // stack). GLFW_KEY_U == 85, but SDL scancode 85 is KP_MULTIPLY -
        // the reason the U keybind showed "Keypad *" and never fired.
        // SDL_SCANCODE_U = 24 (USB HID usage id, same space as W=26/F5=62).
        this.craftmine$keyUnlocks = new KeyMapping("key.unlocks", 24 /* SDL_SCANCODE_U */, KeyMapping.Category.MISC);
        try {
            Field field = Options.class.getField("keyMappings");
            field.setAccessible(true);
            KeyMapping[] current = (KeyMapping[]) field.get(this);
            KeyMapping[] extended = new KeyMapping[current.length + 1];
            System.arraycopy(current, 0, extended, 0, current.length);
            extended[current.length] = this.craftmine$keyUnlocks;
            field.set(this, extended);
            dev.craftmine.Craftmine.LOGGER.info("Craftmine: key.unlocks registered (default U) and appended to Options.keyMappings ({} mappings)", extended.length);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot append key.unlocks to Options.keyMappings", e);
        }
    }

    @Override
    public KeyMapping craftmine$keyUnlocks() {
        return this.craftmine$keyUnlocks;
    }
}
