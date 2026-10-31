/*
 * Craftmine-added data component types (ported from the 25w14craftmine DataComponents diff).
 */
package dev.craftmine.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import dev.craftmine.item.MobTrophyInfo;
import dev.craftmine.item.component.ItemExchangeValue;
import dev.craftmine.item.component.RoomerinoComponentino;
import dev.craftmine.item.component.WorldModifiers;
import dev.craftmine.block.TrophyType;
import dev.craftmine.level.DimensionSpecialEffects;
import net.minecraft.world.level.dimension.LevelStem;
import dev.craftmine.mines.SpecialMine;

public final class CraftmineDataComponents {
    private CraftmineDataComponents() {
    }

    public static final DataComponentType<ItemExchangeValue> EXCHANGE_VALUE = register("exchange_value",
        builder -> builder.persistent(ItemExchangeValue.CODEC).networkSynchronized(ItemExchangeValue.STREAM_CODEC));
    public static final DataComponentType<Unit> WORLD_EFFECT_UNLOCK = register("world_effect_unlock",
        builder -> builder.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));
    public static final DataComponentType<Unit> WORLD_EFFECT_HINT = register("world_effect_uhint",
        builder -> builder.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));
    public static final DataComponentType<Unit> MINE_ACTIVE = register("mine_active",
        builder -> builder.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));
    public static final DataComponentType<SpecialMine> SPECIAL_MINE = register("special_mine",
        builder -> builder.persistent(SpecialMine.CODEC).networkSynchronized(SpecialMine.STREAM_CODEC));
    public static final DataComponentType<Boolean> MINE_COMPLETED = register("mine_completed",
        builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final DataComponentType<WorldModifiers> WORLD_MODIFIERS = register("world_modifiers",
        builder -> builder.persistent(WorldModifiers.CODEC).networkSynchronized(WorldModifiers.STREAM_CODEC).cacheEncoding());
    public static final DataComponentType<RoomerinoComponentino> ROOM = register("instant_room",
        builder -> builder.persistent(RoomerinoComponentino.CODEC).networkSynchronized(RoomerinoComponentino.STREAM_CODEC));
    public static final DataComponentType<TrophyType> TROPHY_TYPE = register("trophy/type",
        builder -> builder.persistent(TrophyType.CODEC).networkSynchronized(TrophyType.STREAM_CODEC));
    public static final DataComponentType<MobTrophyInfo> MOB_TROPHY_TYPE = register("mob_trophy/type",
        builder -> builder.persistent(MobTrophyInfo.CODEC).networkSynchronized(MobTrophyInfo.STREAM_CODEC));
    public static final DataComponentType<DimensionSpecialEffects.Sky> SKY = register("sky",
        builder -> builder.persistent(DimensionSpecialEffects.Sky.CODEC).networkSynchronized(DimensionSpecialEffects.Sky.STREAM_CODEC));
    public static final DataComponentType<ResourceKey<LevelStem>> DIMENSION_ID = register("dimension_id",
        builder -> builder.persistent(ResourceKey.codec(Registries.LEVEL_STEM)).networkSynchronized(ResourceKey.streamCodec(Registries.LEVEL_STEM)).cacheEncoding());

    private static <T> DataComponentType<T> register(String name, java.util.function.Consumer<DataComponentType.Builder<T>> builderConsumer) {
        DataComponentType.Builder<T> builder = DataComponentType.builder();
        builderConsumer.accept(builder);
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.withDefaultNamespace(name), builder.build());
    }

    public static void bootstrap() {
        // static initialization performs registration
    }
}
