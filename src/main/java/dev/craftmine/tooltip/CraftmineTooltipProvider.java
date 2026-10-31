package dev.craftmine.tooltip;

import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;

/**
 * Craftmine's player-aware tooltip contract, carried over from 25w14craftmine
 * where TooltipProvider.addToTooltip still received the viewing Player and the
 * full ItemStack. 26.3's vanilla TooltipProvider dropped both parameters, so
 * craftmine components implement this interface instead and ItemStackMixin
 * routes the calls.
 */
public interface CraftmineTooltipProvider {
    void addToTooltip(Item.TooltipContext tooltipContext, Consumer<Component> consumer, TooltipFlag tooltipFlag, @Nullable Player player, ItemStack itemStack);
}
