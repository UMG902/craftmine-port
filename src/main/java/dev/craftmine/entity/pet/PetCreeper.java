package dev.craftmine.entity.pet;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The pet creeper companion: the silent type. Entirely the shared pet base
 * — no goals, sounds, or quirks of its own. It exists as its own class
 * because the unlock tree registers a distinct entity type for it (and its
 * renderer), and because a creeper that will never explode is its own kind
 * of charm.
 */
public class PetCreeper extends AbstractPet {

    public PetCreeper(EntityType<? extends PetCreeper> type, Level level) {
        super(type, level);
    }
}
