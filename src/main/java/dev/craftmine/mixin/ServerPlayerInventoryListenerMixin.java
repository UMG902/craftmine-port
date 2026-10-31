/*
 * Mixin: craftmine additions to ServerPlayer's anonymous ContainerListener
 * (ports the 25w14craftmine UnlockCondition.onObtainedItem dispatch).
 *
 * Fork call site: the ServerPlayer slot listener fired onObtainedItem right
 * after the INVENTORY_CHANGED criteria trigger. 26.3's anonymous listener is
 * ServerPlayer$2 (CFR-verified: slotChanged -> InventoryChangeTrigger.trigger
 * guarded by slot.container == player inventory), so this mixin targets the
 * anonymous class by name and fires right after that trigger - covering
 * every way an item enters the inventory (pickup, drops, fishing, ...).
 */
package dev.craftmine.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import dev.craftmine.unlock.UnlockCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerPlayer$2")
public abstract class ServerPlayerInventoryListenerMixin {
    @Shadow
    private ServerPlayer this$0;

    @Inject(method = "slotChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/triggers/InventoryChangeTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)V", shift = At.Shift.AFTER))
    private void craftmine$onObtainedItem(AbstractContainerMenu abstractContainerMenu, int n, ItemStack itemStack, CallbackInfo ci) {
        UnlockCondition.onObtainedItem(this.this$0.level(), this.this$0, itemStack);
    }
}
