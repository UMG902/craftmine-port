package dev.craftmine.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.craftmine.duck.CraftmineItemLayerDuck;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineDataComponents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Task 60: while the mine item's foil type is resolved (stack context is
 * available here, never later), stamp the layer with the won/lost glint kind
 * derived from the mine_completed component. A completed-but-not-won mine
 * renders the red glint, a won mine the green one.
 */
@Mixin(CuboidItemModelWrapper.class)
public abstract class CuboidItemModelWrapperMixin {
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;setFoilType(Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"))
    private void craftmine$markMineGlintKind(ItemStackRenderState itemStackRenderState, ItemStack itemStack, ItemModelResolver itemModelResolver, ItemDisplayContext itemDisplayContext, ClientLevel clientLevel, ItemOwner itemOwner, int n, CallbackInfo callbackInfo, @Local(ordinal = 0) ItemStackRenderState.LayerRenderState layerRenderState) {
        Boolean bl = itemStack.get(CraftmineDataComponents.MINE_COMPLETED);
        if (itemStack.is(CraftmineContent.MINE) && bl != null) {
            ((CraftmineItemLayerDuck)(Object)layerRenderState).craftmine$setGlintKind(bl != false ? 1 : 2);
        }
    }
}
