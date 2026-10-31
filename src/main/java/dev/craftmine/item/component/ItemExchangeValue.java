package dev.craftmine.item.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import dev.craftmine.duck.CraftmineDuckBridge;
import dev.craftmine.exchange.CraftmineExchangeValues;
import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.registry.CraftmineRegistries;
import dev.craftmine.tooltip.CraftmineTooltipProvider;
import dev.craftmine.unlock.PlayerUnlock;
import org.jetbrains.annotations.Nullable;

/**
 * The traitor-villager exchange price of an item, in XP-ish "value" units.
 * The base value is scaled by every active unlock's item-tag/item factors,
 * then sums the exchange values of nested container and bundle contents,
 * and finally multiplies by the stack size. Hidden on anything that also
 * carries world modifiers (ingredients show their effects instead).
 */
public record ItemExchangeValue(float value) implements CraftmineTooltipProvider {

    private static final double DISPLAY_ROUNDING = 1000.0;

    public static ItemExchangeValue of(float value) {
        return new ItemExchangeValue(value);
    }

    public static final ItemExchangeValue NO_EXCHANGE = new ItemExchangeValue(0.0F);

    public static final Codec<ItemExchangeValue> CODEC =
        ExtraCodecs.NON_NEGATIVE_FLOAT.xmap(ItemExchangeValue::new, ItemExchangeValue::value);
    public static final StreamCodec<ByteBuf, ItemExchangeValue> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.FLOAT, ItemExchangeValue::value, ItemExchangeValue::new);

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> lines, TooltipFlag flag,
                             @Nullable Player player, ItemStack stack) {
        if (stack.has(CraftmineDataComponents.WORLD_MODIFIERS)) {
            return;
        }
        float total = this.getValue(player, stack);
        double rounded = Math.round((double) total * DISPLAY_ROUNDING) / DISPLAY_ROUNDING;
        lines.accept(Component.translatable("item.exchange_value",
            Component.literal("" + rounded).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.YELLOW));
        lines.accept(Component.translatable("world.effect.convert"));
    }

    /**
     * Full exchange value of the stack: base value scaled by active unlock
     * factors, plus everything inside the container/bundle slots, all times
     * the stack size. The accumulation order mirrors the original exactly.
     */
    public float getValue(@Nullable Player player, ItemStack stack) {
        float scaled = this.value;
        if (player != null) {
            for (Holder<PlayerUnlock> unlock : CraftmineRegistries.PLAYER_UNLOCK.listElements().toList()) {
                if (!CraftmineDuckBridge.isActive(player, unlock)) {
                    continue;
                }
                PlayerUnlock active = unlock.value();
                for (Map.Entry<TagKey<Item>, Float> tagFactor : active.experienceFactorForItemTag().entrySet()) {
                    if (stack.is(tagFactor.getKey())) {
                        scaled *= tagFactor.getValue();
                    }
                }
                for (Map.Entry<Item, Float> itemFactor : active.experienceFactorForItem().entrySet()) {
                    if (stack.is(itemFactor.getKey())) {
                        scaled *= itemFactor.getValue();
                    }
                }
            }
        }
        scaled += this.nestedValue(player, stacksIn(stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems()));
        scaled += this.nestedValue(player, stacksIn(stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).items()));
        return (float) stack.getCount() * scaled;
    }

    /** Flattens a nested item component (container or bundle) into real stacks. */
    private static List<ItemStack> stacksIn(Iterable<net.minecraft.world.item.ItemStackTemplate> templates) {
        List<ItemStack> stacks = new ArrayList<>();
        for (net.minecraft.world.item.ItemStackTemplate template : templates) {
            stacks.add(new ItemStack(template.item(), template.count(), template.components()));
        }
        return stacks;
    }

    private float nestedValue(@Nullable Player player, Iterable<ItemStack> items) {
        float sum = 0.0F;
        for (ItemStack item : items) {
            sum += CraftmineExchangeValues.forStack(item).getValue(player, item);
        }
        return sum;
    }
}
