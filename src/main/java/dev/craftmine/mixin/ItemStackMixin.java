package dev.craftmine.mixin;

import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.craftmine.tooltip.CraftmineTooltips;
import net.minecraft.world.item.Item;

/**
 * Appends craftmine component tooltips (world modifiers, rooms, skies, trophies,
 * exchange value) with the player-aware contract craftmine 25w14 used.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "addDetailsToTooltip", at = @At("TAIL"))
    private void craftmine$appendComponentTooltips(Item.TooltipContext context, TooltipDisplay display, @Nullable Player player, TooltipFlag tooltipFlag, Consumer<Component> builder, CallbackInfo ci) {
        CraftmineTooltips.appendCraftmineTooltips((ItemStack)(Object)this, context, display, player, tooltipFlag, builder);
    }
}
