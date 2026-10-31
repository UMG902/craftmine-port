package dev.craftmine.item.component;

import java.util.function.Consumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import dev.craftmine.tooltip.CraftmineTooltipProvider;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Marker payload on the shimmering key naming the special hub room it
 * opens. The component stores the room id WITHOUT the "hub/room/" path
 * prefix (compact save data + language keys); the structure id is rebuilt
 * on demand.
 */
public record RoomerinoComponentino(Identifier id) implements CraftmineTooltipProvider {

    public static final Codec<RoomerinoComponentino> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Identifier.CODEC.fieldOf("structure").forGetter(RoomerinoComponentino::id)
    ).apply(instance, RoomerinoComponentino::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoomerinoComponentino> STREAM_CODEC =
        StreamCodec.composite(Identifier.STREAM_CODEC, RoomerinoComponentino::id, RoomerinoComponentino::new);

    public static final String STRUCTURE_PREFIX = "hub/room/";
    public static final String LANGUAGE_PREFIX = "room";

    public Identifier structureId() {
        return idToStructure(this.id);
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> lines, TooltipFlag flag,
                             @Nullable Player player, ItemStack stack) {
        String roomKey = this.id.toLanguageKey(LANGUAGE_PREFIX);
        lines.accept(Component.translatable("item.minecraft.shimmering_key.room", Component.translatable(roomKey))
            .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Strips the hub/room/ prefix: full structure id -> compact component id. */
    public static Identifier structureToId(Identifier structureId) {
        return structureId.withPath(path -> StringUtils.removeStart(path, STRUCTURE_PREFIX));
    }

    /** Re-adds the hub/room/ prefix: compact component id -> full structure id. */
    public static Identifier idToStructure(Identifier id) {
        return id.withPrefix(STRUCTURE_PREFIX);
    }
}
