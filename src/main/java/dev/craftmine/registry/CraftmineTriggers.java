/*
 * Craftmine-added criteria triggers (ported from the 25w14craftmine CriteriaTriggers diff).
 */
package dev.craftmine.registry;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.ItemUsedOnLocationTrigger;
import net.minecraft.advancements.triggers.PlayerTrigger;
import dev.craftmine.unlock.UsedExitEyeTrigger;
import net.minecraft.advancements.triggers.ConsumeItemTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class CraftmineTriggers {
    private CraftmineTriggers() {
    }

    public static final UsedExitEyeTrigger USED_EXIT_EYE = register("used_exit_eye", new UsedExitEyeTrigger());
    public static final ConsumeItemTrigger CONSUMED_HUNDRED_POISONOUS_POTATOES = register("consume_poisonous_potatoes", new ConsumeItemTrigger());
    public static final PlayerTrigger PLAYER_UNLOCK_BOUGHT = register("player_unlock_bought", new PlayerTrigger());
    public static final PlayerTrigger PLAYER_UNLOCK_UNLOCKED = register("player_unlock_unlocked", new PlayerTrigger());
    public static final PlayerTrigger MINE_CRAFTER_UPGRADED = register("mine_crafter_upgraded", new PlayerTrigger());
    public static final PlayerTrigger MINE_REVISITOR_ACTIVATED = register("mine_revisitor_activated", new PlayerTrigger());
    public static final PlayerTrigger LEVEL_COMPLETED = register("level_completed", new PlayerTrigger());
    public static final PlayerTrigger SPECIAL_MINE_COMPLETED = register("special_mine_completed", new PlayerTrigger());
    public static final PlayerTrigger LEVEL_FAILED = register("level_failed", new PlayerTrigger());
    public static final ItemUsedOnLocationTrigger INVENTORY_CASHED_IN = register("inventory_cashed_in", new ItemUsedOnLocationTrigger());

    private static <T extends net.minecraft.advancements.triggers.CriterionTrigger<?>> T register(String name, T trigger) {
        return Registry.register(BuiltInRegistries.TRIGGER_TYPES, Identifier.withDefaultNamespace(name), trigger);
    }

    public static void bootstrap() {
        // static initialization performs registration
    }
}
