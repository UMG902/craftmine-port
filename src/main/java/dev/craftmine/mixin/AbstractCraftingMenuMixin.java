/*
 * Task 31 (unlock effects): the crafting result slot and crafting grid slots
 * of EVERY AbstractCraftingMenu (crafting table AND inventory) are gated on
 * the root "Crafting" unlock - the fork's cxz anonymous dab/Slot subclasses.
 * Without the "Crafting" unlock the result slot and the grid are locked
 * crafter-style, so you start unable to craft anywhere; buying "Crafting"
 * enables the crafting table, "Inventory Crafting" additionally enables the
 * inventory 2x2 (dynamic part lives on InventoryMenu.craftmine$updateUnlockSlots).
 *
 * The redirect handlers are non-static on purpose: they run in instance
 * context (this = the AbstractCraftingMenu) so the grid handler can resolve
 * the menu owner for the unlock check. owner() is safe during construction:
 * both CraftingMenu and InventoryMenu assign their player before adding
 * slots (verified in 26.3 bytecode).
 *
 * Task 85 (EULA de-shadow): the shipped InventoryMenu copy is gone; the
 * fork's always-3x3 inventory grid (super(null, 0, 3, 3)) is reproduced by
 * bumping width/height/craftSlots at the tail of THIS constructor when the
 * instance is an InventoryMenu - this runs BEFORE InventoryMenu's ctor body
 * adds the slots, so addCraftingGridSlots lays out 9 grid slots exactly like
 * the fork copy did. CraftingMenu (3x3 crafting table) and MineCraftingMenu
 * (fork class) pass their own dims and are left untouched.
 */
package dev.craftmine.mixin;

import dev.craftmine.inventory.CraftmineCraftingSlot;
import dev.craftmine.inventory.CraftmineResultSlot;
import dev.craftmine.inventory.CraftmineUnlockGate;
import dev.craftmine.unlock.PlayerUnlocks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractCraftingMenu.class)
public abstract class AbstractCraftingMenuMixin {

    @Shadow
    @Final
    @Mutable
    private int width;

    @Shadow
    @Final
    @Mutable
    private int height;

    @Shadow
    @Final
    @Mutable
    protected CraftingContainer craftSlots;

    @Shadow
    protected abstract Player owner();

    @Inject(
        method = "<init>(Lnet/minecraft/world/inventory/MenuType;III)V",
        at = @At("TAIL")
    )
    private void craftmine$inventoryMenuAlways3x3(MenuType<?> menuType, int containerId, int pWidth, int pHeight, CallbackInfo ci) {
        if (!((Object) this instanceof InventoryMenu)) {
            return;
        }
        // 25w14craftmine cze: super(null, 0, 3, 3); the visible/usable subset
        // is driven by unlocks every tick (InventoryMenuMixin.craftmine$
        // $updateUnlockSlots). Bump the shared state before the subclass
        // body adds result + grid slots.
        this.width = 3;
        this.height = 3;
        this.craftSlots = new TransientCraftingContainer((AbstractContainerMenu) (Object) this, 3, 3);
    }

    /*
     * Fork cze.d/c: the grid behaves as 2x2 for recipe placement unless the
     * "3x3 Inventory Crafting" unlock is active. getGridWidth/getGridHeight
     * are declared on AbstractCraftingMenu (vanilla InventoryMenu only
     * inherits them), so the unlock-aware override lives here, guarded to
     * InventoryMenu instances; every other menu keeps the vanilla body.
     */
    @Inject(method = "getGridWidth", at = @At("HEAD"), cancellable = true)
    private void craftmine$inventoryGridWidth(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof InventoryMenu) {
            cir.setReturnValue(CraftmineUnlockGate.isActive(owner(), PlayerUnlocks.INVENTORY_CRAFTING_3X3) ? 3 : 2);
        }
    }

    @Inject(method = "getGridHeight", at = @At("HEAD"), cancellable = true)
    private void craftmine$inventoryGridHeight(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof InventoryMenu) {
            cir.setReturnValue(CraftmineUnlockGate.isActive(owner(), PlayerUnlocks.INVENTORY_CRAFTING_3X3) ? 3 : 2);
        }
    }

    @Redirect(
        method = "addResultSlot",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/Container;III)Lnet/minecraft/world/inventory/ResultSlot;"
        )
    )
    private ResultSlot craftmine$gatedResultSlot(Player player, CraftingContainer craftSlots, net.minecraft.world.Container container, int id, int x, int y) {
        return new CraftmineResultSlot(player, craftSlots, container, id, x, y);
    }

    @Redirect(
        method = "addCraftingGridSlots",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/Container;III)Lnet/minecraft/world/inventory/Slot;"
        )
    )
    private Slot craftmine$gatedGridSlot(Container container, int index, int x, int y) {
        Player player = owner();
        return new CraftmineCraftingSlot(container, index, x, y, player);
    }
}
