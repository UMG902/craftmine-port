/*
 * Task 31 (unlock effects): item pickup only fills the hotbar + the unlocked
 * pocket rows. The fork's Inventory (cue) k() - first-empty-slot scan - caps
 * the scan range at 9 + 9 per pockets tier (inventory_slots_1/2/3); stack
 * merging (getSlotWithRemainingSpace) is deliberately NOT gated (fork
 * d(dcs) scans everything).
 *
 * 26.3's Inventory.getFreeSlot is the pickup path (Inventory.add), so the
 * mixin applies the same cap. The player's own unlock state decides.
 */
package dev.craftmine.mixin;

import dev.craftmine.inventory.CraftmineUnlockGate;
import dev.craftmine.unlock.PlayerUnlocks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private net.minecraft.core.NonNullList<ItemStack> items;

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void craftmine$pocketsFreeSlot(CallbackInfoReturnable<Integer> cir) {
        int unlockedSize = 9;
        net.minecraft.world.entity.player.Player self = ((Inventory) (Object) this).player;
        if (CraftmineUnlockGate.isActive(self, PlayerUnlocks.INVENTORY_SLOTS_1)) {
            unlockedSize += 9;
        }
        if (CraftmineUnlockGate.isActive(self, PlayerUnlocks.INVENTORY_SLOTS_2)) {
            unlockedSize += 9;
        }
        if (CraftmineUnlockGate.isActive(self, PlayerUnlocks.INVENTORY_SLOTS_3)) {
            unlockedSize += 9;
        }
        for (int i = 0; i < Math.min(unlockedSize, this.items.size()); ++i) {
            if (!this.items.get(i).isEmpty()) continue;
            cir.setReturnValue(i);
            return;
        }
        cir.setReturnValue(-1);
    }
}
