package dev.craftmine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Registers all craftmine content during the vanilla bootstrap window.
 *
 * 26.3 freezes the built-in registries inside BuiltInRegistries.bootStrap()
 * (createContents -> freeze -> validate). Custom content must be registered
 * after vanilla's contents are created but BEFORE freeze(), so we inject right
 * before the freeze() invocation. The mod entrypoint is far too late — the
 * server would crash with "Registry is already frozen".
 */
@Mixin(BuiltInRegistries.class)
public abstract class BuiltInRegistriesMixin {

    @Inject(
        method = "bootStrap",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/registries/BuiltInRegistries;freeze()V"
        )
    )
    private static void craftmine$registerContent(CallbackInfo ci) {
        dev.craftmine.registry.CraftmineBootstrap.bootstrapAll();
    }

    /**
     * validate()'s per-registry lambda logs "Registry X was empty after loading"
     * for the craftmine registries - they are INTENTIONALLY empty at boot because
     * their entry fill is deferred to post-datapack-load. Skip the whole
     * per-registry check for them (they are not DefaultedRegistries either, so
     * nothing else in the lambda applies).
     */
    @Inject(
        method = "lambda$validate$0",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void craftmine$skipEmptyLogForDeferredRegistries(net.minecraft.core.Registry<?> root,
                                                                    net.minecraft.core.Registry<?> registry,
                                                                    CallbackInfo ci) {
        if (dev.craftmine.registry.CraftmineRegistries.isDeferredFillRegistry(registry)) {
            ci.cancel();
        }
    }
}
