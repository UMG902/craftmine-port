package dev.craftmine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.ReloadableServerResources;

/**
 * 26.3 binds item components during resource loading
 * (ReloadableServerResources.updateComponentsAndStaticRegistryTags), NOT
 * during vanilla bootstrap. Craftmine-only registries (world effects, player
 * unlocks) build ItemStacks in their static initializers, so they must be
 * populated after that binding — this is the earliest safe window on both the
 * dedicated server and the integrated client server.
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {

    @Inject(method = "updateComponentsAndStaticRegistryTags", at = @At("TAIL"))
    private void craftmine$registerCustomRegistries(CallbackInfo ci) {
        dev.craftmine.registry.CraftmineBootstrap.bootstrapCustomRegistries();
    }
}
