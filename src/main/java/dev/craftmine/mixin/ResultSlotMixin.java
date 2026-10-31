/*
 * Task 31 (unlock effects): result-slot onTake behaviour.
 *  (1) "Artisan Crafting" (crafting_efficiency): ingredients are no longer
 *      consumed when crafting (fork's dab.a: the shrink(1) per grid slot only
 *      runs when the player LACKS the unlock or is in creative).
 *  (2) The craftedItem unlock conditions ("3x3 Inventory Crafting" and
 *      "Artisan Crafting" become visible after crafting) are evaluated after
 *      each craft - the fork calls its crafted trigger (dnw.a) from the same
 *      method; the port's UnlockCondition.onCraftedItem existed with no
 *      caller.
 */
package dev.craftmine.mixin;

import dev.craftmine.inventory.CraftmineUnlockGate;
import dev.craftmine.unlock.PlayerUnlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Shadow
    @Final
    CraftingContainer craftSlots;

    @Shadow
    @Final
    private Player player;

    /**
     * The ingredient consumption step of onTake. The fork only consumes when
     * the player does NOT have crafting_efficiency (creative players always
     * consume, fork-faithful: !hasEfficiency || creative).
     */
    @Redirect(
        method = "onTake",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/CraftingContainer;removeItem(II)Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private ItemStack craftmine$artisanNoConsume(CraftingContainer container, int slot, int count) {
        boolean hasEfficiency = CraftmineUnlockGate.isActive(this.player, PlayerUnlocks.CRAFTING_EFFICIENCY);
        if (hasEfficiency && !this.player.hasInfiniteMaterials()) {
            // onTake discards the return value and re-reads the slot - just
            // hand back the current stack without shrinking it.
            return container.getItem(slot);
        }
        return container.removeItem(slot, count);
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void craftmine$fireCraftedConditions(Player takenBy, ItemStack crafted, CallbackInfo ci) {
        if (takenBy instanceof ServerPlayer serverPlayer && !takenBy.level().isClientSide()) {
            dev.craftmine.unlock.UnlockCondition.onCraftedItem(takenBy.level(), serverPlayer, crafted);
        }
    }
}
