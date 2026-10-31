package dev.craftmine.mixin;

import dev.craftmine.registry.CraftmineContent;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Task 60: "No data fixer registered for minecraft:pet_*" bootstrap noise.
 * Util.doFetchChoiceType logs an ERROR for every entity type whose id is not
 * in the current datafixer schema, then returns null - for the fork's
 * brand-new entity types (15 pets + angry_ghast) that null is CORRECT (there
 * is no legacy NBT to upgrade; behavior is identical to any new vanilla
 * entity until Mojang adds schema entries). The error is only raised because
 * CHECK_DATA_FIXER_SCHEMA is enabled in this environment. Silence it for
 * port-registered entities only; unknown ids keep logging.
 */
@Mixin(Util.class)
public abstract class UtilFetchChoiceTypeMixin {
    @Redirect(method = "doFetchChoiceType", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V"))
    private static void craftmine$skipPortEntityDataFixerError(Logger logger, String string, Object object) {
        if (object instanceof String string2 && CraftmineContent.DATAPACK_SCHEMA_IDS.contains(string2)) {
            return;
        }
        logger.error(string, object);
    }
}
