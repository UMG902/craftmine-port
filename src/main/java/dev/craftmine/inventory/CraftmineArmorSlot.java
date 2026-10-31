/*
 * Task 31 (unlock effects): armor slot gated on the "Armaments" unlock
 * (awz.aB = armaments) - the fork's cze anonymous cyc (ArmorSlot) subclass:
 * mayPlace = armaments && vanilla, isActive = armaments && vanilla. Without
 * "Armaments" the four armor slots are locked crafter-style and shift-click
 * equipping is rejected (the InventoryMenu overlay's quickMoveStack).
 */
package dev.craftmine.inventory;

import dev.craftmine.inventory.CraftmineUnlockGate;
import dev.craftmine.unlock.PlayerUnlocks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;

public class CraftmineArmorSlot extends ArmorSlot {
    private final LivingEntity craftmine$owner;

    public CraftmineArmorSlot(Container container, LivingEntity owner, EquipmentSlot slot, int slotIndex, int x, int y, Identifier emptyIcon) {
        super(container, owner, slot, slotIndex, x, y, emptyIcon);
        this.craftmine$owner = owner;
    }

    private boolean craftmine$armamentsUnlocked() {
        return this.craftmine$owner instanceof net.minecraft.world.entity.player.Player player
            && CraftmineUnlockGate.isActive(player, PlayerUnlocks.ARMAMENTS);
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return super.mayPlace(itemStack) && this.craftmine$armamentsUnlocked();
    }

    @Override
    public boolean isActive() {
        return super.isActive() && this.craftmine$armamentsUnlocked();
    }
}
