package dev.craftmine.registry;

/**
 * Central bootstrap for every craftmine registration.
 *
 * Two windows are involved:
 * - vanilla bootstrap (before BuiltInRegistries.freeze()): vanilla-extended
 *   registries (attributes, memory modules, data components, content, triggers).
 * - after item components are bound (ReloadableServerResources
 *   .updateComponentsAndStaticRegistryTags): the craftmine-only registries
 *   (world effects, player unlocks, special mines, events), because their
 *   static initializers build ItemStacks — impossible before 26.3 binds item
 *   components post-datapack-load.
 */
public final class CraftmineBootstrap {

    private static boolean customRegistriesInitialized = false;
    private static boolean worldEffectsInitialized = false;

    private CraftmineBootstrap() {}

    /** Runs during vanilla bootstrap, right before BuiltInRegistries.freeze(). */
    public static void bootstrapAll() {
        // craftmine registry OBJECTS must be known to the root
        // registry-of-registries pre-freeze (network holderRegistry lookups
        // resolve through RegistryLayer.STATIC_ACCESS = fromRegistryOfRegistries
        // (BuiltInRegistries.REGISTRY) - see CraftmineRegistries.registerRootRegistries)
        CraftmineRegistries.registerRootRegistries();
        // vanilla registries that the fork extended (attributes, memory modules)
        CraftmineAttributes.bootstrap();
        CraftmineMemoryModules.bootstrap();
        // the fork's ACTING activity (warden boss intro). MUST be pre-freeze:
        // left to lazy <clinit>, the first touch is WardenAiMixin.getActivities
        // (first warden brain) -> Activity.register into a frozen registry ->
        // "Registry is already frozen" crash.
        CraftmineActivities.boop();
        // mod content: blocks, items, entities, menus, block entities, data components
        CraftmineDataComponents.bootstrap();
        CraftmineContent.bootstrap();
        // World effects are referenced by world_modifiers inside villager_trade
        // JSONs, which RegistryDataLoader decodes before the post-component hook.
        // Their static bootstrap only registers immutable definitions (item model
        // ids are resolved from registry keys); it does not construct effect stacks.
        // Register effects and sets now, but leave them unfrozen until the normal
        // custom-registry phase so tags and registry lifecycle remain intact.
        bootstrapWorldEffectsForDatapackDecode();
        // particle types (mine_travel) - MUST be pre-freeze; if left to lazy
        // <clinit> the first touch is the client provider mixin during
        // Minecraft.<init> -> "Registry is already frozen" client crash
        CraftmineParticleTypes.bootstrap();
        // custom triggers
        CraftmineTriggers.bootstrap();
        // entity sub-predicate codecs (special_mine / world_effects / unlocks)
        // - advancement JSONs reference these ids during the datapack load,
        //   so they must exist in the sub-predicate registry pre-datapack
        CraftmineEntityPredicates.bootstrap();
        // loot context parameter sets (hub_reward) - MUST be pre-freeze; if
        // left to lazy <clinit> the first touch is ServerLevelMixin.dropRewards
        // at the first mine exit that rolls room rewards ("keys to roll" > 0),
        // which Registry.register's into the already-frozen CONTEXT_KEY_SET
        // registry -> "Registry is already frozen" server crash (2026-10-30
        // user log, crash-2026-10-30_21.00.05-server.txt). The shipped
        // loot_table/gameplay/room_rewards.json is also typed
        // "minecraft:hub_reward" and decodes at datapack load (post-freeze),
        // so the set must exist before the first server reload, not just
        // before the first reward drop. Same lazy-clinit bug class as
        // CraftmineActivities/CraftmineParticleTypes above.
        CraftmineLootContextParamSets.bootstrap();
        // sound events: vanilla-removed pet sounds (idempotent) + the
        // nothingtoseehere craftmine sounds (the and_action jukebox disc is
        // referenced by the shipped jukebox_song datapack entry)
        dev.craftmine.registry.CraftmineSoundEvents.bootstrap();
        // the "endish" multi-noise preset (endish.json parameter list ships in
        // the datapack and must resolve during registry load)
        dev.craftmine.level.biome.CraftmineMultiNoisePresets.bootstrap();
    }

    private static void bootstrapWorldEffectsForDatapackDecode() {
        if (worldEffectsInitialized) {
            return;
        }
        worldEffectsInitialized = true;
        CraftmineRegistries.bootstrapWorldEffects();
    }

    /**
     * Runs once components are bound, from ReloadableServerResourcesMixin.
     * updateComponentsAndStaticRegistryTags() is also invoked on /reload,
     * so this is idempotent.
     */
    public static void bootstrapCustomRegistries() {
        if (customRegistriesInitialized) {
            return;
        }
        customRegistriesInitialized = true;
        // craftmine-only registries: world effects, player unlocks, special mines, events
        CraftmineRegistries.bootstrap();
        // vanilla-namespace pet sound instances (26.3 binds registry holders at
        // freeze, so id->instance lookups only work from this post-freeze point on)
        dev.craftmine.registry.CraftmineSoundEvents.resolveVanillaInstances();
    }

    /**
     * True once the deferred custom-registry fill has happened (or started).
     * MappedRegistryMixin keys its freeze-cancel on this: while false, vanilla
     * BuiltInRegistries.freeze() must skip the (still entry-less) craftmine
     * registries; once true, freeze() must go through so they end up frozen AND
     * tag-bound (freeze is vanilla's tag binder - registries without datapack
     * tag files are only ever bound there, and the configuration-phase
     * TagNetworkSerialization.serializeTagsToNetwork throws "Tags not bound"
     * on any registry whose TagSet was never bound).
     */
    public static boolean isCustomRegistriesInitialized() {
        return customRegistriesInitialized;
    }

    /** True after the world-effect entries have been registered during vanilla bootstrap. */
    public static boolean areWorldEffectsInitialized() {
        return worldEffectsInitialized;
    }
}
