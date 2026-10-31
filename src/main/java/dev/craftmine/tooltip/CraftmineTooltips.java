package dev.craftmine.tooltip;

import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.entity.player.Player;
import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.level.DimensionSpecialEffects;

/**
 * Replays the tooltip append order of the 25w14craftmine-forked
 * ItemStack.addDetailsToTooltip for craftmine-only components. Invoked from
 * ItemStackMixin at TAIL of the vanilla method.
 */
public final class CraftmineTooltips {
    private CraftmineTooltips() {
    }

    public static void appendCraftmineTooltips(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, @Nullable Player player, TooltipFlag flag, Consumer<Component> consumer) {
        append(stack, CraftmineDataComponents.WORLD_MODIFIERS, context, display, player, consumer, flag);
        append(stack, CraftmineDataComponents.ROOM, context, display, player, consumer, flag);
        append(stack, CraftmineDataComponents.SKY, context, display, player, consumer, flag);
        append(stack, CraftmineDataComponents.MOB_TROPHY_TYPE, context, display, player, consumer, flag);
        append(stack, CraftmineDataComponents.TROPHY_TYPE, context, display, player, consumer, flag);
        if (stack.has(CraftmineDataComponents.WORLD_EFFECT_UNLOCK)) {
            consumer.accept(Component.translatable("world.effect.unlock"));
        }
        if (stack.has(CraftmineDataComponents.EXCHANGE_VALUE)) {
            append(stack, CraftmineDataComponents.EXCHANGE_VALUE, context, display, player, consumer, flag);
        } else if (dev.craftmine.exchange.CraftmineExchangeValues.hasFallback(stack.getItem()) && display.shows(CraftmineDataComponents.EXCHANGE_VALUE)) {
            // Task 51: items the fork never priced (post-1.21.5 additions) - show
            // the derived table value the same way component stacks show theirs.
            dev.craftmine.exchange.CraftmineExchangeValues.forStack(stack).addToTooltip(context, consumer, flag, player, stack);
        }
    }

    private static <T extends CraftmineTooltipProvider> void append(ItemStack stack, DataComponentType<T> type, Item.TooltipContext context, TooltipDisplay display, @Nullable Player player, Consumer<Component> consumer, TooltipFlag flag) {
        if (!display.shows(type)) {
            return;
        }
        T component = stack.get(type);
        if (component != null) {
            component.addToTooltip(context, consumer, flag, player, stack);
        }
    }
}
