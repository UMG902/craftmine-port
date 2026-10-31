package dev.craftmine.item;

import dev.craftmine.registry.CraftmineDataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class MineItem
extends Item {
    public MineItem(Item.Properties properties) {
        super(properties);
    }

    /*
     * Task 60 (fork-faithful): a finished mine's item glints - the fork picked
     * the green map_won_glint / red map_lost_glint textures (hdi.b/hdi.c) for
     * won/lost mines; the active state does not glint. The client chooses the
     * colored glint via CraftmineGlints + CraftmineItemLayerDuck from the
     * mine_completed component; here we only gate the foil flag.
     */
    @Override
    public boolean isFoil(ItemStack itemStack) {
        return itemStack.has(CraftmineDataComponents.MINE_COMPLETED) || super.isFoil(itemStack);
    }
}
