package dev.craftmine.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.craftmine.client.render.CraftmineGlints;
import dev.craftmine.duck.CraftmineItemLayerDuck;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Task 60: swap the mine item's quads for won/lost-glint-rematerialized
 * copies right before the submit leaves the layer (the only point where the
 * layer's stack-derived kind can still reach the per-quad render types).
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemStackRenderStateLayerMixin implements CraftmineItemLayerDuck {
    @Shadow
    private ItemQuads quads;

    @Unique
    private int craftmine$glintKind;

    @Override
    public int craftmine$glintKind() {
        return this.craftmine$glintKind;
    }

    @Override
    public void craftmine$setGlintKind(int kind) {
        this.craftmine$glintKind = kind;
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void craftmine$resetGlintKind(CallbackInfo callbackInfo) {
        this.craftmine$glintKind = 0;
    }

    @Inject(method = "submit", at = @At(value = "HEAD", target = ""))
    private void craftmine$remapGlintQuads(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int n, int n2, int n3, CallbackInfo callbackInfo) {
        if (this.craftmine$glintKind != 0) {
            this.quads = CraftmineGlints.remapQuads(this.quads, this.craftmine$glintKind);
        }
    }
}
