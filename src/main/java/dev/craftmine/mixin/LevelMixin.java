package dev.craftmine.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Craftmine additions to the common Level.
 *
 * In 25w14craftmine, Level.dimensionTypeRegistration was a non-final private
 * field with a public setDimensionType(Holder) mutator (used when entering /
 * leaving mines). 26.3 moved the field to Level as private final, so we shadow
 * it with @Mutable here. The injected methods are picked up by
 * ServerLevel's `implements CraftmineLevelDuck` (ServerLevelMixin) through
 * ordinary inheritance.
 */
@Mixin(Level.class)
public abstract class LevelMixin implements dev.craftmine.duck.CraftmineLevelDuck {

    @Mutable
    @Final
    @Shadow
    private Holder<DimensionType> dimensionTypeRegistration;

    /** The level's dimension type before the first Dimension Control skybox gate; null until then. */
    @Unique
    private Holder<DimensionType> craftmine$preSkyDimensionType;

    @Override
    public void craftmine$setDimensionType(Holder<DimensionType> holder) {
        this.dimensionTypeRegistration = holder;
    }

    /**
     * Direct registration-field swap without dispatching through the (client
     * overridden) craftmine$setDimensionType - the declaring-class shadow
     * lives here, so the client mixin routes its Dimension Control swap
     * through this. Task 44.
     */
    @Override
    public void craftmine$swapDimensionTypeRegistration(Holder<DimensionType> holder) {
        this.dimensionTypeRegistration = holder;
    }

    @Override
    public Holder<DimensionType> craftmine$dimensionTypeRegistration() {
        return this.dimensionTypeRegistration;
    }

    @Override
    public void craftmine$applySkyDimensionType(Holder<DimensionType> holder) {
        // Capture the untouched registration exactly once, BEFORE the first
        // sky-driven overwrite, so a cleared slot can restore it (mines swap
        // dimension types through craftmine$setDimensionType for travel; the
        // capture only ever snapshots the type seen at first sky use).
        if (this.craftmine$preSkyDimensionType == null) {
            this.craftmine$preSkyDimensionType = this.dimensionTypeRegistration;
        }
        this.dimensionTypeRegistration = holder;
    }

    @Override
    public void craftmine$restorePreSkyDimensionType() {
        if (this.craftmine$preSkyDimensionType != null) {
            this.dimensionTypeRegistration = this.craftmine$preSkyDimensionType;
            this.craftmine$preSkyDimensionType = null;
        }
    }

    @Override
    public dev.craftmine.server.TheGame craftmine$theGame() {
        return dev.craftmine.server.CraftmineServerHooks.theGame();
    }

    /*
     * FT-8 gameplay layer: fork Level.getRainLevel/getThunderLevel overrode
     * the weather queries while a mine forces them (WorldEffects.ETERNAL_RAIN
     * / ETERNAL_LIGHTNING):
     *   rain    = 1.0 while ETERNAL_RAIN or ETERNAL_LIGHTNING is active
     *   thunder = 1.0 while ETERNAL_LIGHTNING is active
     * The onMineEnter handlers (WorldEffects) only pin the initial levels and
     * stop the weather cycle; these overrides are what keep the sky forced
     * for the whole mine session (fork-identical: they also short-circuit the
     * lerp, so client and server agree).
     */
    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getRainLevel(F)F", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private float craftmine$eternalRain(float original) {
        Level self = (Level) (Object) this;
        if (dev.craftmine.server.CraftmineGameplayEffects.isActive(self, dev.craftmine.mines.WorldEffects.ETERNAL_RAIN)
            || dev.craftmine.server.CraftmineGameplayEffects.isActive(self, dev.craftmine.mines.WorldEffects.ETERNAL_LIGHTNING)) {
            return 1.0F;
        }
        return original;
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getThunderLevel(F)F", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private float craftmine$eternalLightning(float original) {
        Level self = (Level) (Object) this;
        if (dev.craftmine.server.CraftmineGameplayEffects.isActive(self, dev.craftmine.mines.WorldEffects.ETERNAL_LIGHTNING)) {
            return 1.0F;
        }
        return original;
    }
}
