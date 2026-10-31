/*
 * Task 85 (EULA de-shadow): replaces the shipped InventoryMenu overlay copy
 * (task 31) with mixins on the vanilla 26.3 class. The fork (25w14craftmine
 * cze) delta over vanilla 26.3 is small and fully reproduced here:
 *  - the crafting grid is ALWAYS 3x3 (done in AbstractCraftingMenuMixin's
 *    ctor tail: width/height/craftSlots bump runs before the slots are added)
 *  - the four armor slots are CraftmineArmorSlot ("Armaments" gate) - the
 *    NEW redirect below
 *  - getInputGridSlots / getGridWidth / getGridHeight - unlock-aware
 *    overwrites (grid behaves as 2x2 for recipe placement unless the
 *    "3x3 Inventory Crafting" unlock is active)
 *  - quickMoveStack uses the fork's menu layout (result 0, grid 1-9,
 *    armor 10-13, main 14-40, hotbar 41-49, offhand 50) with the Armaments
 *    gate on shift-click equipping
 *  - craftmine$updateUnlockSlots (duck CraftmineCraftingMenuDuck) drives the
 *    visible/usable subset every tick; client InventoryScreenMixin and
 *    server ServerPlayerMixin already call it through the duck interface
 * Everything else (ctor body after the slots, slotsChanged, removed,
 * stillValid, canTakeItemForPickAll, getResultSlot, getCraftSlots,
 * getRecipeBookType, owner(), isHotbarSlot) is identical between the fork
 * overlay and vanilla 26.3 and needs no mixin.
 *
 * This mixin extends AbstractCraftingMenu (the target's SUPERCLASS - allowed)
 * purely so the inherited members (slots, moveItemStackTo) are reachable;
 * the super(...) call in the private ctor never runs.
 */
package dev.craftmine.mixin;

import dev.craftmine.duck.CraftmineCraftingMenuDuck;
import dev.craftmine.duck.CraftmineGateSlot;
import dev.craftmine.duck.CraftmineSlotDuck;
import dev.craftmine.inventory.CraftmineArmorSlot;
import dev.craftmine.inventory.CraftmineUnlockGate;
import net.minecraft.resources.Identifier;
import dev.craftmine.unlock.PlayerUnlocks;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractCraftingMenu implements CraftmineCraftingMenuDuck {

    @Shadow
    @Final
    private Player owner;

    private InventoryMenuMixin() {
        super(null, 0, 2, 2);
    }

    /*
     * The fork's armor slot swap (cze anonymous ArmorSlot subclass):
     * mayPlace/isActive additionally require "Armaments" (CraftmineArmorSlot).
     */
    @Redirect(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;IIILnet/minecraft/resources/Identifier;)Lnet/minecraft/world/inventory/ArmorSlot;"
        )
    )
    private ArmorSlot craftmine$gatedArmorSlot(Container container, LivingEntity livingOwner, EquipmentSlot equipmentSlot, int slotIndex, int x, int y, Identifier emptyIcon) {
        return new CraftmineArmorSlot(container, livingOwner, equipmentSlot, slotIndex, x, y, emptyIcon);
    }

    /**
     * Fork: the grid is 3x3 (slots 1-9), not vanilla's 2x2 (slots 1-4).
     */
    @Overwrite
    public List<Slot> getInputGridSlots() {
        return this.slots.subList(1, 10);
    }

    /*
     * Fork cze.d/c: the grid behaves as 2x2 for recipe placement unless the
     * "3x3 Inventory Crafting" unlock is active - handled by guarded
     * getGridWidth/getGridHeight injections in AbstractCraftingMenuMixin
     * (vanilla InventoryMenu only INHERITS those methods, so they cannot be
     * overwritten from a mixin on InventoryMenu itself).
     */

    /*
     * Fork cze.b(cuf, int) with the fork's menu layout. Armor / offhand
     * shift-click targets additionally require "Armaments".
     */
    @Overwrite
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack target = slot.getItem();
            original = target.copy();
            EquipmentSlot equipmentSlot = this.owner.getEquipmentSlotForItem(target);
            if (slotIndex == 0) {
                if (!this.moveItemStackTo(target, 14, 50, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(target, original);
            } else if (slotIndex >= 1 && slotIndex < 10) {
                if (!this.moveItemStackTo(target, 14, 50, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 10 && slotIndex < 14) {
                if (!this.moveItemStackTo(target, 14, 50, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !this.slots.get(14 - 1 - equipmentSlot.getIndex()).hasItem()) {
                // armor slots are 10-13, head first: ARMOR_SLOT_END - 1 - index
                int armorSlot = 13 - equipmentSlot.getIndex();
                if (!CraftmineUnlockGate.isActive(this.owner, PlayerUnlocks.ARMAMENTS) || !this.moveItemStackTo(target, armorSlot, armorSlot + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (equipmentSlot == EquipmentSlot.OFFHAND && !this.slots.get(50).hasItem()) {
                if (!CraftmineUnlockGate.isActive(this.owner, PlayerUnlocks.ARMAMENTS) || !this.moveItemStackTo(target, 50, 51, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 14 && slotIndex < 41) {
                if (!this.moveItemStackTo(target, 41, 50, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 41 && slotIndex < 50) {
                if (!this.moveItemStackTo(target, 14, 41, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(target, 14, 50, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (target.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY, original);
            } else {
                slot.setChanged();
            }
            if (target.getCount() == original.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, target);
            if (slotIndex == 0) {
                player.drop(target, false, Prediction.PREDICTED);
            }
        }
        return original;
    }

    /**
     * The fork's cze.e(player): unlock-driven crafting grid layout, run every
     * tick on the client (InventoryScreenMixin) and server-side while the
     * inventory screen is open (ServerPlayerMixin - keeps the gate
     * server-authoritative).
     */
    @Override
    public void craftmine$updateUnlockSlots() {
        boolean has3x3 = CraftmineUnlockGate.isActive(this.owner, PlayerUnlocks.INVENTORY_CRAFTING_3X3);
        boolean has2x2 = CraftmineUnlockGate.isActive(this.owner, PlayerUnlocks.INVENTORY_CRAFTING);
        List<Slot> grid = this.getInputGridSlots();
        Slot result = this.getResultSlot();
        if (has3x3) {
            for (int row = 0; row < 3; ++row) {
                for (int col = 0; col < 3; ++col) {
                    Slot slot = grid.get(col + row * 3);
                    ((CraftmineSlotDuck) slot).craftmine$setPosition(85 + col * 18, 11 + row * 18);
                    ((CraftmineGateSlot) slot).craftmine$setUnlockActive(true);
                }
            }
            ((CraftmineGateSlot) result).craftmine$setUnlockActive(true);
        } else if (has2x2) {
            for (int row = 0; row < 3; ++row) {
                for (int col = 0; col < 3; ++col) {
                    Slot slot = grid.get(col + row * 3);
                    ((CraftmineSlotDuck) slot).craftmine$setPosition(98 + col * 18, 18 + row * 18);
                    ((CraftmineGateSlot) slot).craftmine$setUnlockActive(col < 2 && row < 2);
                }
            }
            ((CraftmineGateSlot) result).craftmine$setUnlockActive(true);
        } else {
            for (Slot slot : grid) {
                ((CraftmineGateSlot) slot).craftmine$setUnlockActive(false);
            }
            ((CraftmineGateSlot) result).craftmine$setUnlockActive(false);
        }
    }
}
