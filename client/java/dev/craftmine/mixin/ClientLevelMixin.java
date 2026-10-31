/*
 * ClientLevel craftmine state: unlocked/active world effects + the Dimension
 * Control's active sky and swapped dimension type.
 * The original fork held these directly on ClientLevel.
 */
package dev.craftmine.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import dev.craftmine.level.DimensionSpecialEffects;
import net.minecraft.world.level.dimension.DimensionType;
import dev.craftmine.mines.WorldEffect;
import dev.craftmine.server.SkyDefaults;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.craftmine.duck.CraftmineClientLevelDuck;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin implements CraftmineClientLevelDuck {
    @Unique
    private List<WorldEffect> craftmine$unlockedEffects = new ArrayList<WorldEffect>();
    @Unique
    private List<WorldEffect> craftmine$activeEffects = new ArrayList<WorldEffect>();
    @Unique
    private DimensionSpecialEffects.Sky craftmine$activeSky;
    @Unique
    private Holder<DimensionType> craftmine$dimensionTypeHolder;

    /** The environment attribute system (private final on ClientLevel) - rebuilt when the visual environment changes. */
    @Mutable
    @Final
    @Shadow
    private EnvironmentAttributeSystem environmentAttributes;

    /** ClientLevel's own layer wiring (default dimension/biome/timeline/weather layers + the two sky-flash layers). */
    @Invoker("addEnvironmentAttributeLayers")
    public abstract EnvironmentAttributeSystem.Builder craftmine$addEnvironmentAttributeLayers(EnvironmentAttributeSystem.Builder builder);
    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$initFields(CallbackInfo ci) {
        // Mixin does not merge field initializers on this class-file version.
        if (this.craftmine$unlockedEffects == null) this.craftmine$unlockedEffects = new ArrayList<WorldEffect>();
        if (this.craftmine$activeEffects == null) this.craftmine$activeEffects = new ArrayList<WorldEffect>();
    }

    @Unique
    @Override
    public boolean isEffectUnlocked(WorldEffect worldEffect) {
        return this.craftmine$unlockedEffects.contains(worldEffect);
    }

    @Unique
    @Override
    public boolean isActive(WorldEffect worldEffect) {
        return this.craftmine$activeEffects.contains(worldEffect);
    }

    @Unique
    @Override
    public List<WorldEffect> getActiveEffects() {
        return this.craftmine$activeEffects;
    }

    @Unique
    @Override
    public List<WorldEffect> getUnlockedEffects() {
        return this.craftmine$unlockedEffects;
    }

    @Unique
    @Override
    public void setUnlockedWorldEffects(List<WorldEffect> list) {
        this.craftmine$unlockedEffects.clear();
        this.craftmine$unlockedEffects.addAll(list);
    }

    /**
     * The fork set activeEffects once in ClientLevel's constructor (spawn
     * info); 26.3 has no spawn-info hook, so the packet applies it after the
     * fresh ClientLevel exists (sendLevelInfo ordering guarantees the packet
     * lands after the login/respawn packet).
     */
    @Unique
    @Override
    public void setActiveWorldEffects(List<WorldEffect> list) {
        this.craftmine$activeEffects.clear();
        if (list != null) {
            this.craftmine$activeEffects.addAll(list);
        }
    }


    @Unique
    @Override
    public DimensionSpecialEffects.Sky craftmine$getActiveSky() {
        if (this.craftmine$activeSky != null) {
            return this.craftmine$activeSky;
        }
        return SkyDefaults.forLevel((ClientLevel) (Object) this);
    }

    @Unique
    @Override
    public void craftmine$setActiveSky(DimensionSpecialEffects.Sky sky) {
        this.craftmine$activeSky = sky;
    }

    @Unique
    @Override
    public void craftmine$setDimensionType(Holder<DimensionType> holder) {
        // Capture the REAL current registration FIRST: the duck getter below
        // would return the field we are about to overwrite (the holder cache
        // is assigned before this check - comparing against it compares the
        // value with itself, early-returning every swap).
        Holder<DimensionType> previous = this.craftmine$dimensionTypeRegistration();
        if (holder == this.craftmine$dimensionTypeHolder) {
            return;
        }
        this.craftmine$dimensionTypeHolder = holder;
        if (holder == null) {
            // Defensive only (the menu never produces null): keep the current
            // registration rather than guessing the pre-sky one.
            return;
        }
        if (holder.value().equals(previous.value())) {
            return;
        }
        // The REAL swap: every vanilla consumer (SkyRenderer.extractRenderState's
        // skybox, fog/cloud/lightmap attribute reads, the environment system's
        // timeline layers) reads the registration field. The field's shadow
        // lives on Level (LevelMixin) - route through the duck.
        this.craftmine$swapDimensionTypeRegistration(holder);
        // The environment attribute system bakes the dimension type's visual
        // attributes + timeline layers at construction time (ClientLevel ctor
        // -> addEnvironmentAttributeLayers -> addDefaultLayers), so a swapped
        // type stays invisible to it until it is rebuilt. Re-run ClientLevel's
        // own wiring against the NEW dimensionType() - task 44 (the black
        // sky / frozen sun after an overworld sky pick).
        this.environmentAttributes = this.craftmine$addEnvironmentAttributeLayers(EnvironmentAttributeSystem.builder()).build();
    }

    /** The (possibly packet-modified) dimension type holder for sky gating. */
    @Unique
    @Override
    public Holder<DimensionType> craftmine$dimensionTypeRegistration() {
        if (this.craftmine$dimensionTypeHolder != null) {
            return this.craftmine$dimensionTypeHolder;
        }
        return ((ClientLevel) (Object) this).dimensionTypeRegistration();
    }

    /**
     * Craftmine SkyWindows: every client-side block change updates the window
     * geometry cache incrementally (add/remove the position when a window
     * block - sky, shimmering door, dimension control - appears or
     * disappears). Runs on the render thread, like the rest of the
     * SkyWindowRenderer state.
     */
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void craftmine$skyWindowBlockUpdated(net.minecraft.core.BlockPos blockPos,
            net.minecraft.world.level.block.state.BlockState oldState,
            net.minecraft.world.level.block.state.BlockState newState, int updateFlags, CallbackInfo ci) {
        dev.craftmine.client.render.SkyWindowRenderer.get().onBlockUpdated(blockPos, oldState, newState);
    }
}
