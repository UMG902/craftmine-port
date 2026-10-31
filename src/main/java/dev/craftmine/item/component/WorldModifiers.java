package dev.craftmine.item.component;

import java.util.List;
import java.util.function.Consumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.mines.WorldEffect;
import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.registry.CraftmineRegistries;
import dev.craftmine.tooltip.CraftmineTooltipProvider;
import org.jetbrains.annotations.Nullable;

/**
 * The "mine ingredient" payload: the world effects a mine crafter recipe
 * will apply, plus whether the tooltip should show full descriptions.
 * Locked-but-hinted effects show their unlock hint instead of the real
 * description; a completed-mine status line is prepended when the item
 * came out of a finished mine.
 */
public record WorldModifiers(List<WorldEffect> effects, boolean includeDescription) implements CraftmineTooltipProvider {

    public static final Codec<WorldModifiers> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        WorldEffect.CODEC.listOf().fieldOf("effects").forGetter(WorldModifiers::effects),
        Codec.BOOL.fieldOf("include_description").forGetter(WorldModifiers::includeDescription)
    ).apply(instance, WorldModifiers::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldModifiers> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.registry(CraftmineRegistries.WORLD_EFFECT_KEY).apply(ByteBufCodecs.list()), WorldModifiers::effects,
        ByteBufCodecs.BOOL, WorldModifiers::includeDescription,
        WorldModifiers::new);
    public static final WorldModifiers EMPTY = new WorldModifiers(List.of(), false);

    private static final Component LOCKED_DESCRIPTION = Component.literal("???");
    private static final Component WON = Component.literal("Won").withStyle(ChatFormatting.GREEN);
    private static final Component LOST = Component.literal("Lost").withStyle(ChatFormatting.RED);
    private static final Style HINT_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY);
    private static final String STATUS_PREFIX = "Status: ";
    private static final String EFFECTS_HEADER = "Effects:";
    private static final String EFFECT_INDENT = "  ";

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> lines, TooltipFlag flag,
                             @Nullable Player player, ItemStack stack) {
        Boolean completed = stack.get(CraftmineDataComponents.MINE_COMPLETED);
        if (completed != null) {
            lines.accept(Component.literal(STATUS_PREFIX).append(completed ? WON : LOST).withStyle(ChatFormatting.GRAY));
            lines.accept(CommonComponents.EMPTY);
        }
        boolean hasHint = stack.get(CraftmineDataComponents.WORLD_EFFECT_HINT) != null;
        boolean hasUnlock = stack.get(CraftmineDataComponents.WORLD_EFFECT_UNLOCK) != null;
        if (this.includeDescription) {
            for (WorldEffect effect : this.effects) {
                lines.accept(wrapWithModifier(effect, this.describeEffect(player, effect, hasHint, hasUnlock)));
            }
        } else {
            lines.accept(Component.literal(EFFECTS_HEADER));
            for (WorldEffect effect : this.effects) {
                lines.accept(Component.literal(EFFECT_INDENT).append(wrapWithModifier(effect, effect.name())));
            }
        }
    }

    private Component describeEffect(@Nullable Player player, WorldEffect effect, boolean hinted, boolean gated) {
        boolean unlockedOnLevel = player != null
            && ((CraftmineLevelDuck) (Object) player.level()).isEffectUnlocked(effect);
        if (!unlockedOnLevel && gated) {
            return LOCKED_DESCRIPTION;
        }
        if (hinted) {
            MutableComponent hint = ComponentUtils.mergeStyles(effect.unlockHint().copy(), HINT_STYLE);
            return Component.translatable("unlocks.screen.hint", hint);
        }
        return effect.description();
    }

    /** Appends the XP-modifier note when the effect does not multiply XP by exactly 1. */
    private static Component wrapWithModifier(WorldEffect effect, Component text) {
        float modifier = effect.experienceModifier();
        if (modifier != 1.0F) {
            return Component.translatable("world.effect.experience_modifier", text, modifier);
        }
        return text;
    }
}
