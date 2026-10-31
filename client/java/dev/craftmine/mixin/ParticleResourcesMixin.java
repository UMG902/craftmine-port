package dev.craftmine.mixin;

// NOTE: This mixin intentionally lives in OUR package (dev.craftmine.mixin), NOT in
// net.minecraft.client.particle. A mixin config whose "package" is a vanilla package
// makes Mixin treat every vanilla class in that package as a forbidden reference
// (IllegalClassLoadError "cannot be referenced directly") and crashes the client at
// startup. Private member shadowing works fine cross-package; only the mixin CONFIG
// package must point at our own classes.

import java.util.Map;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.craftmine.registry.CraftmineParticleTypes;
import dev.craftmine.client.render.MineTravelParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Registers the mine_travel particle provider on every ParticleResources
 * instance. In 25w14craftmine vanilla registers it inside ParticleEngine's
 * provider list; 26.3 moved providers into ParticleResources.registerProviders
 * (instance method). The private register(ParticleType,
 * SpriteParticleRegistration) overload cannot be invoked directly (the
 * registration interface is a private inner), so this mixin replicates its
 * exact body: create a MutableSpriteSet (private class - constructed by
 * reflection, since an @Invoker interface could not be classloaded safely at
 * this point), put it into spriteSets under the particle's registry key, and
 * put the provider (built from that set) into providers under the particle's
 * numeric registry id. Hooking the TAIL of registerProviders covers every
 * ParticleResources construction. The sprite set is then filled by the normal
 * particle reload via the spriteSets map.
 */
@Mixin(ParticleResources.class)
public abstract class ParticleResourcesMixin {

    // The field's value type is the private MutableSpriteSet; shadowed with
    // a wildcard since Mixin compares erased descriptors only.
    @Shadow
    private Map<Identifier, ?> spriteSets;

    @Shadow
    private Int2ObjectMap<ParticleProvider<?>> providers;

    @Inject(method = "registerProviders", at = @At("TAIL"))
    private void craftmine$registerMineTravel(CallbackInfo ci) {
        ParticleType<?> type = CraftmineParticleTypes.MINE_TRAVEL;
        SpriteSet spriteSet = craftmine$newMutableSpriteSet();
        ((Map<Identifier, Object>) this.spriteSets).put(BuiltInRegistries.PARTICLE_TYPE.getKey(type), spriteSet);
        this.providers.put(BuiltInRegistries.PARTICLE_TYPE.getId(type), new MineTravelParticle.Provider(spriteSet));
    }

    /**
     * MutableSpriteSet is a PRIVATE inner class - constructed reflectively.
     * An @Invoker("<init>") factory cannot be used: calling it would classload
     * the invoker interface, which sponge-mixin rejects until the target class
     * (ParticleResources$MutableSpriteSet) has been loaded and transformed -
     * and at registerProviders TAIL time the inner class may not have loaded.
     */
    private static SpriteSet craftmine$newMutableSpriteSet() {
        try {
            Class<?> clazz = Class.forName("net.minecraft.client.particle.ParticleResources$MutableSpriteSet");
            var ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (SpriteSet) ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate ParticleResources$MutableSpriteSet", e);
        }
    }
}
