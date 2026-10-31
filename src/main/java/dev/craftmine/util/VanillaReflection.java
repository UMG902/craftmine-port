package dev.craftmine.util;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Reflection helpers for touching vanilla private static state.
 *
 * Why not accessor mixins? Sponge-mixin (0.17.4+mixin.0.8.7) rejects ANY direct
 * classload of a mixin interface ("Illegal classload request ... cannot be
 * referenced directly") until its TARGET class has been loaded and transformed
 * - and Knot transforms classes lazily. Registration code that runs BEFORE the
 * target class loads (client entrypoint runs before EntityRenderers/ItemModels
 * are ever touched; bootstrapAll runs before
 * MultiNoiseBiomeSourceParameterList$Preset) would crash on an accessor call.
 * Field reflection through Class.getDeclaredField triggers the target's clinit
 * deterministically at the call site, which is exactly the ordering we need.
 */
public final class VanillaReflection {
    private VanillaReflection() {
    }

    /** Reads a private static field, triggering the owner's clinit on first touch. */
    public static Object readStaticField(Class<?> owner, String fieldName) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + owner.getName() + "." + fieldName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> readStaticMap(Class<?> owner, String fieldName) {
        return (Map<K, V>) readStaticField(owner, fieldName);
    }
}
