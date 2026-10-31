package dev.craftmine.duck;

import net.minecraft.client.KeyMapping;

/**
 * Exposes the craftmine-added key mappings hung on Options.
 * The fork added keyUnlocks directly to Options (1.21.5); the port cannot
 * edit the class, so the mapping lives in a mixin field and is reached
 * through this duck.
 */
public interface CraftmineOptionsDuck {
    KeyMapping craftmine$keyUnlocks();
}
