package dev.craftmine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;

import dev.craftmine.registry.CraftmineBootstrap;
import dev.craftmine.registry.CraftmineRegistries;

/**
 * BuiltInRegistries.freeze() iterates the ROOT registry (which now contains the
 * craftmine registries, see CraftmineRegistries.registerRootRegistries) and
 * freezes every entry. The craftmine registries' ENTRY fill is deferred to
 * post-datapack-load (item components are not bound at bootstrap time), so they
 * must stay writable until CraftmineRegistries.bootstrap() fills them and
 * freezes them explicitly. Without this, the deferred fill crashes with
 * "Registry is already frozen" and the world load aborts.
 *
 * CRITICAL (world-creation "Tags not bound" fix): vanilla MappedRegistry.freeze()
 * is ALSO the point where registry tags get BOUND
 * (allTags = TagSet.fromMap(frozenTags)). The resource-load tag pipeline
 * (TagLoader.loadPendingTags) only creates PendingTags for registries that
 * actually ship tag JSON files; for everything else the freeze()-binding is the
 * ONLY binding they ever get. ServerConfigurationPacketListenerImpl ->
 * SynchronizeRegistriesTask -> TagNetworkSerialization.serializeTagsToNetwork
 * iterates EVERY registry of the STATIC layer (RegistrySynchronization
 * .networkSafeRegistries) and calls getTags() - an unbound TagSet throws
 * "IllegalStateException: Tags not bound" there, the error is swallowed
 * ("suppressing error"), the configuration task never completes and the client
 * hangs forever on the world-creation screen.
 *
 * So the cancel here must apply ONLY while the deferred fill has not happened
 * yet (vanilla bootstrap window). After CraftmineBootstrap.bootstrapCustomRegistries()
 * runs, freeze() calls MUST go through so the registries end up frozen AND
 * tag-bound, exactly like vanilla registries.
 */
@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin {

    @Inject(method = "freeze", at = @At("HEAD"), cancellable = true)
    private void craftmine$deferFreezeForCustomRegistries(CallbackInfoReturnable<Registry<?>> cir) {
        Registry<?> registry = (Registry<?>) (Object) this;
        boolean worldEffectRegistry = registry == CraftmineRegistries.WORLD_EFFECT
                || registry == CraftmineRegistries.WORLD_EFFECT_SET;
        // World effects are now populated before datapack decode. Let vanilla's
        // root freeze bind their holder values before villager_trade codecs resolve
        // them. The other craftmine registries still have to remain writable until
        // the post-component bootstrap.
        if (CraftmineRegistries.isDeferredFillRegistry(registry)
                && !CraftmineBootstrap.isCustomRegistriesInitialized()
                && !(worldEffectRegistry && CraftmineBootstrap.areWorldEffectsInitialized())) {
            cir.setReturnValue(registry);
        }
    }
}
