/*
 * FT-8 gameplay layer - the fork's TRAITOR villager profession.
 *
 * The fork registered minecraft:traitor (POI = lodestone, ambient sound =
 * pillager) selling the ILLAGER world-effect ingredients (pillagers,
 * vindicators, evokers, ravagers, illusioners) for emeralds. 26.3 moved
 * trades into datapack files (trade_set/villager_trade), so:
 *   - this class holds the profession key + registers the profession via the
 *     vanilla VillagerProfession.bootstrap registry (mixin tail)
 *   - the trades themselves ship as data under
 *     data/minecraft/{trade_set,villager_trade,tags/villager_trade}/.../traitor/
 * mirroring the fork's EmeraldForItems/ItemsForEmeralds listings 1:1.
 */
package dev.craftmine.server;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.trading.TradeSet;

public final class CraftmineTraitorVillager {
    public static final ResourceKey<VillagerProfession> TRAITOR =
        ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.withDefaultNamespace("traitor"));

    private static final ResourceKey<TradeSet> TRAITOR_LEVEL_1 = tradeSet("traitor/level_1");
    private static final ResourceKey<TradeSet> TRAITOR_LEVEL_2 = tradeSet("traitor/level_2");
    private static final ResourceKey<TradeSet> TRAITOR_LEVEL_3 = tradeSet("traitor/level_3");
    private static final ResourceKey<TradeSet> TRAITOR_LEVEL_4 = tradeSet("traitor/level_4");
    private static final ResourceKey<TradeSet> TRAITOR_LEVEL_5 = tradeSet("traitor/level_5");

    private CraftmineTraitorVillager() {
    }

    private static ResourceKey<TradeSet> tradeSet(String path) {
        return ResourceKey.create(Registries.TRADE_SET, Identifier.withDefaultNamespace(path));
    }

    public static VillagerProfession register(Registry<VillagerProfession> registry) {
        Int2ObjectMap<ResourceKey<TradeSet>> trades = new Int2ObjectOpenHashMap<>();
        trades.put(1, TRAITOR_LEVEL_1);
        trades.put(2, TRAITOR_LEVEL_2);
        trades.put(3, TRAITOR_LEVEL_3);
        trades.put(4, TRAITOR_LEVEL_4);
        trades.put(5, TRAITOR_LEVEL_5);
        return Registry.register(registry, TRAITOR, new VillagerProfession(
            net.minecraft.network.chat.Component.translatable("entity.minecraft.villager.traitor"),
            holder -> holder.is(PoiTypes.LODESTONE),
            holder -> holder.is(PoiTypes.LODESTONE),
            com.google.common.collect.ImmutableSet.of(),
            com.google.common.collect.ImmutableSet.of(),
            SoundEvents.PILLAGER_AMBIENT,
            trades));
    }
}
