package dev.craftmine.block;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;

import dev.craftmine.tooltip.CraftmineTooltipProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * Variants of the trophy block: the earned gold statue, the mega-spud joke
 * variant and the "no medal" consolation prize (handed out by the shimmering
 * door when offered a trial key). Drives the item tooltip + block state.
 */
public enum TrophyType implements StringRepresentable, CraftmineTooltipProvider {
    GOLD("gold"),
    MEGA_SPUD("mega_spud"),
    NO_MEDAL("no_medal");

    public static final Codec<TrophyType> CODEC = StringRepresentable.fromEnum(TrophyType::values);
    public static final StreamCodec<ByteBuf, TrophyType> STREAM_CODEC =
        ByteBufCodecs.idMapper(ordinal -> TrophyType.values()[ordinal], Enum::ordinal);

    private final String name;

    TrophyType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    @Override
    public void addToTooltip(Item.TooltipContext tooltipContext, Consumer<Component> tooltip, TooltipFlag tooltipFlag, @Nullable Player player, ItemStack itemStack) {
        tooltip.accept(Component.translatable("trophy." + this.name).withStyle(ChatFormatting.GRAY));
    }
}
