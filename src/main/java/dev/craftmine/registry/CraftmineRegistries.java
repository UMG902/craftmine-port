/*
 * Craftmine-added registries (ported from the 25w14craftmine BuiltInRegistries/Registries diffs).
 */
package dev.craftmine.registry;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import dev.craftmine.unlock.PlayerUnlock;
import dev.craftmine.unlock.PlayerUnlocks;
import dev.craftmine.mines.MineEvent;
import dev.craftmine.mines.SpecialMine;
import dev.craftmine.mines.SpecialMines;
import dev.craftmine.mines.WorldEffect;
import dev.craftmine.mines.WorldEffectSet;
import dev.craftmine.mines.WorldEffects;

public final class CraftmineRegistries {
    private CraftmineRegistries() {
    }

    public static final ResourceKey<Registry<PlayerUnlock>> PLAYER_UNLOCK_KEY = createRegistryKey("player_unlock");
    public static final ResourceKey<Registry<WorldEffect>> WORLD_EFFECT_KEY = createRegistryKey("world_effect");
    public static final ResourceKey<Registry<WorldEffectSet>> WORLD_EFFECT_SET_KEY = createRegistryKey("world_effect_set");
    public static final ResourceKey<Registry<SpecialMine>> SPECIAL_MINE_KEY = createRegistryKey("special_mine");
    public static final ResourceKey<Registry<com.mojang.serialization.MapCodec<? extends MineEvent>>> MINE_EVENT_TYPE_KEY = createRegistryKey("mine_event_type");

    public static final Registry<PlayerUnlock> PLAYER_UNLOCK = new MappedRegistry<>(PLAYER_UNLOCK_KEY, Lifecycle.stable(), false);
    public static final Registry<WorldEffect> WORLD_EFFECT = new MappedRegistry<>(WORLD_EFFECT_KEY, Lifecycle.stable(), false);
    public static final Registry<WorldEffectSet> WORLD_EFFECT_SET = new MappedRegistry<>(WORLD_EFFECT_SET_KEY, Lifecycle.stable(), false);
    public static final Registry<SpecialMine> SPECIAL_MINE = new MappedRegistry<>(SPECIAL_MINE_KEY, Lifecycle.stable(), false);
    public static final Registry<com.mojang.serialization.MapCodec<? extends MineEvent>> MINE_EVENT_TYPE = new MappedRegistry<>(MINE_EVENT_TYPE_KEY, Lifecycle.stable(), false);

    private static <T> ResourceKey<Registry<T>> createRegistryKey(String name) {
        return ResourceKey.createRegistryKey(Identifier.withDefaultNamespace(name));
    }

    public static void bootstrapWorldEffects() {
        WorldEffects.bootstrap(WORLD_EFFECT);
        WorldEffects.sets(WORLD_EFFECT_SET);
    }

    public static void bootstrap() {
        PlayerUnlocks.bootstrap(PLAYER_UNLOCK);
        SpecialMines.bootstrap(SPECIAL_MINE);
        MineEvent.bootstrap(MINE_EVENT_TYPE);
        freezeToleratingDatapackTags((MappedRegistry<PlayerUnlock>)PLAYER_UNLOCK);
        // WORLD_EFFECT and WORLD_EFFECT_SET are filled and frozen during the
        // early vanilla bootstrap so their holder values are available while
        // datapack codecs (notably villager_trade/world_modifiers) decode.
        freezeToleratingDatapackTags((MappedRegistry<SpecialMine>)SPECIAL_MINE);
        freezeToleratingDatapackTags((MappedRegistry<com.mojang.serialization.MapCodec<? extends MineEvent>>)MINE_EVENT_TYPE);
    }

    /**
     * Freezes the registry (which also BINDS its tags - vanilla binds allTags
     * from frozenTags inside freeze(), and registries without datapack tag
     * files are bound nowhere else). Tolerates the one case where freeze() is
     * illegal: if a datapack ships tags for one of these registries,
     * TagLoader's PendingTags.apply() -> bindTags() already ran earlier inside
     * updateComponentsAndStaticRegistryTags (vanilla body) and freeze() then
     * throws "Tags already present before freezing". In that case the registry
     * is already tag-bound; it just stays unfrozen, which is harmless.
     */
    private static void freezeToleratingDatapackTags(MappedRegistry<?> registry) {
        try {
            registry.freeze();
        } catch (IllegalStateException e) {
            if (!String.valueOf(e.getMessage()).contains("Tags already present")) {
                throw e;
            }
        }
    }

    /**
     * Registers the craftmine registry OBJECTS into the root
     * registry-of-registries (BuiltInRegistries.REGISTRY), mirroring what the
     * fork's modified BuiltInRegistries clinit did via internalRegister.
     *
     * WITHOUT this, RegistryLayer.STATIC_ACCESS =
     * RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY) has no
     * player_unlock entry, and every ByteBufCodecs.holderRegistry lookup fails at
     * NETWORK time: server encode of clientbound/minecraft:player_unlocks throws
     * "IllegalStateException: Missing registry: ResourceKey[minecraft:root /
     * minecraft:player_unlock]" and kicks the player on first join.
     *
     * MUST run inside the pre-freeze window (CraftmineBootstrap.bootstrapAll),
     * i.e. before BuiltInRegistries.bootStrap() calls freeze().
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerRootRegistries() {
        registerInRoot(PLAYER_UNLOCK_KEY, PLAYER_UNLOCK);
        registerInRoot(WORLD_EFFECT_KEY, WORLD_EFFECT);
        registerInRoot(WORLD_EFFECT_SET_KEY, WORLD_EFFECT_SET);
        registerInRoot(SPECIAL_MINE_KEY, SPECIAL_MINE);
        registerInRoot(MINE_EVENT_TYPE_KEY, MINE_EVENT_TYPE);
    }

    private static <T> void registerInRoot(ResourceKey<Registry<T>> key, Registry<T> registry) {
        Registry.register((Registry) BuiltInRegistries.REGISTRY, (ResourceKey) key, registry);
    }

    /**
     * True for the 5 craftmine registries whose ENTRY fill is deferred to
     * post-datapack-load (CraftmineBootstrap.bootstrapCustomRegistries). These
     * must NOT be frozen by BuiltInRegistries.freeze() (which iterates the root
     * registry) - the deferred fill registers entries and freezes them itself.
     * Used by MappedRegistryMixin (freeze cancel) and the validate-log skip.
     */
    public static boolean isDeferredFillRegistry(Registry<?> registry) {
        return registry == PLAYER_UNLOCK || registry == WORLD_EFFECT || registry == WORLD_EFFECT_SET
            || registry == SPECIAL_MINE || registry == MINE_EVENT_TYPE;
    }
}
