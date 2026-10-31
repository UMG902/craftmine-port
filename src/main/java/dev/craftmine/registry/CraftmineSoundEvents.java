package dev.craftmine.registry;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound events used by the craftmine fork.
 *
 * 26.3 registry model note: MappedRegistry.register creates Holder.References
 * that stay UNBOUND until freeze() (freeze iterates byValue and calls
 * bindValue). Pre-freeze there is therefore NO working id->instance lookup
 * (getValue throws "Trying to access unbound value"), and re-registering an
 * already-keyed id throws "Duplicate registration". Hence the two phases:
 *
 * - bootstrap() runs pre-freeze from CraftmineBootstrap.bootstrapAll:
 *   registers the craftmine-own "nothingtoseehere" sounds (their ids do not
 *   exist in vanilla, including the and_action jukebox disc whose datapack
 *   entry jukebox_song/and_action.json resolves it at datapack-load time) and
 *   any of the vanilla-namespace pet sounds whose key vanished entirely from
 *   26.3 (26.3 keeps most base ids as keys via the *_SoundVariants systems,
 *   but a few really are gone).
 *
 * - resolveVanillaInstances() runs post-freeze from
 *   CraftmineBootstrap.bootstrapCustomRegistries (after holders are bound):
 *   assigns the vanilla-namespace pet sound fields, preferring an instance we
 *   registered ourselves and falling back to the registry value (26.3 keeps
 *   entity.cat.* / entity.chicken.* as keys registered by the cat/chicken
 *   sound-variant bootstrap; entity.wolf.step even still has a constant, but
 *   resolving uniformly by id keeps this class simple).
 */
public final class CraftmineSoundEvents {
    private CraftmineSoundEvents() {
    }

    /** Vanilla-namespace sounds used by the ported pet entity code. */
    public static SoundEvent CAT_AMBIENT;
    public static SoundEvent CAT_STRAY_AMBIENT;
    public static SoundEvent CAT_HISS;
    public static SoundEvent CAT_PURR;
    public static SoundEvent CAT_PURREOW;
    public static SoundEvent CHICKEN_AMBIENT;
    public static SoundEvent CHICKEN_HURT;
    public static SoundEvent CHICKEN_DEATH;
    public static SoundEvent CHICKEN_STEP;
    public static SoundEvent WOLF_STEP;

    private static final String[] VANILLA_PATHS = {
        "entity.cat.ambient", "entity.cat.stray_ambient", "entity.cat.hiss",
        "entity.cat.purr", "entity.cat.purreow", "entity.chicken.ambient",
        "entity.chicken.hurt", "entity.chicken.death", "entity.chicken.step",
        "entity.wolf.step",
    };

    /** Ids we registered ourselves pre-freeze (key was absent in 26.3). */
    private static final Map<String, SoundEvent> OWNED = new HashMap<>();

    // ---- craftmine-own sounds (nothingtoseehere namespace) ----
    public static final SoundEvent MUSIC_DISC_AND_ACTION = registerCraftmine("and_action");
    public static final SoundEvent UI_PLAYER_UNLOCK_SUCCESS = registerCraftmine("ui.player_unlock_success");
    public static final SoundEvent UI_PLAYER_UNLOCK_FAIL = registerCraftmine("ui.player_unlock_fail");
    public static final SoundEvent VILLAGER_CROWD_START = registerCraftmine("crowd_start");
    public static final SoundEvent VILLAGER_CROWD_CHEER = registerCraftmine("crowd_cheer");
    public static final SoundEvent VILLAGER_CROWD_WAITING = registerCraftmine("crowd_waiting");

    private static Identifier nothingtoseehere(String path) {
        return Identifier.fromNamespaceAndPath("nothingtoseehere", path);
    }

    private static SoundEvent registerCraftmine(String path) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, nothingtoseehere(path),
            SoundEvent.createVariableRangeEvent(nothingtoseehere(path)));
    }

    /**
     * Pre-freeze phase: make sure every needed id HAS a key. The
     * nothingtoseehere sounds are always fresh registrations; vanilla-namespace
     * ids are only registered when 26.3 dropped the key entirely (containsKey
     * on an existing key would trip the duplicate-registration check).
     */
    public static void bootstrap() {
        for (String path : VANILLA_PATHS) {
            Identifier id = Identifier.withDefaultNamespace(path);
            if (!BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
                SoundEvent sound = Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                    SoundEvent.createVariableRangeEvent(id));
                OWNED.put(path, sound);
            }
        }
    }

    /**
     * Post-freeze phase: bind the vanilla-namespace fields. Our own
     * registrations are known instances; everything else resolves through the
     * now-bound holders.
     */
    public static void resolveVanillaInstances() {
        for (String path : VANILLA_PATHS) {
            SoundEvent own = OWNED.get(path);
            if (own != null) {
                assign(path, own);
                continue;
            }
            Identifier id = Identifier.withDefaultNamespace(path);
            SoundEvent resolved = BuiltInRegistries.SOUND_EVENT.getValue(id);
            if (resolved == null) {
                throw new IllegalStateException("Sound event missing from 26.3 registry: " + id);
            }
            assign(path, resolved);
        }
    }

    private static void assign(String path, SoundEvent sound) {
        switch (path) {
            case "entity.cat.ambient" -> CAT_AMBIENT = sound;
            case "entity.cat.stray_ambient" -> CAT_STRAY_AMBIENT = sound;
            case "entity.cat.hiss" -> CAT_HISS = sound;
            case "entity.cat.purr" -> CAT_PURR = sound;
            case "entity.cat.purreow" -> CAT_PURREOW = sound;
            case "entity.chicken.ambient" -> CHICKEN_AMBIENT = sound;
            case "entity.chicken.hurt" -> CHICKEN_HURT = sound;
            case "entity.chicken.death" -> CHICKEN_DEATH = sound;
            case "entity.chicken.step" -> CHICKEN_STEP = sound;
            case "entity.wolf.step" -> WOLF_STEP = sound;
            default -> throw new IllegalArgumentException(path);
        }
    }
}
