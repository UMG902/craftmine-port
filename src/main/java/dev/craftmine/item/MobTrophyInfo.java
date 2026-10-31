package dev.craftmine.item;

import java.util.function.Consumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.core.registries.codec.RegistryFixedCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import dev.craftmine.tooltip.CraftmineTooltipProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Payload of the mob trophy item: which entity type the trophy shows and
 * whether it is "shiny" (the rare 1% crafting roll). The codec accepts a
 * bare entity-type holder as an alternative encoding for older data.
 */
public record MobTrophyInfo(Holder<EntityType<?>> type, boolean shiny) implements CraftmineTooltipProvider {

    public static final Codec<MobTrophyInfo> CODEC = Codec.withAlternative(
        RecordCodecBuilder.create(instance -> instance.group(
            RegistryCodecs.holder(Registries.ENTITY_TYPE).fieldOf("type").forGetter(MobTrophyInfo::type),
            Codec.BOOL.fieldOf("shiny").forGetter(MobTrophyInfo::shiny)
        ).apply(instance, MobTrophyInfo::new)),
        RegistryCodecs.holder(Registries.ENTITY_TYPE),
        holder -> new MobTrophyInfo(holder, false));
    public static final StreamCodec<RegistryFriendlyByteBuf, MobTrophyInfo> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.holderRegistry(Registries.ENTITY_TYPE), MobTrophyInfo::type,
        ByteBufCodecs.BOOL, MobTrophyInfo::shiny,
        MobTrophyInfo::new);

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> lines, TooltipFlag flag,
                             @Nullable Player player, ItemStack stack) {
        lines.accept(Component.translatable("item.minecraft.mob_trophy.entity", this.type.value().getDescription()));
        if (this.shiny) {
            lines.accept(Component.translatable("item.minecraft.mob_trophy.shiny").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
