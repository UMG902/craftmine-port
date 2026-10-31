package dev.craftmine.registry;

import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Craftmine-added loot context parameter sets and loot table keys.
 * The matching JSONs ship with the bundled craftmine datapack.
 *
 * Registration timing (load-bearing): the ContextKeySet registration happens
 * in the static initializer, so this class MUST be initialized during the
 * vanilla bootstrap window, BEFORE BuiltInRegistries.freeze() - exactly like
 * CraftmineActivities/CraftmineParticleTypes. CraftmineBootstrap.bootstrapAll()
 * calls bootstrap() for that, from BuiltInRegistriesMixin's injection point
 * right before the freeze() invocation.
 *
 * If the class is instead left to lazy <clinit>, the first touch is
 * ServerLevelMixin.dropRewards at the first mine exit with "keys to roll" > 0,
 * and Registry.register hits the already-frozen CONTEXT_KEY_SET registry ->
 * "Registry is already frozen" server crash (user log 2026-10-30 20:00). A
 * failed <clinit> also permanently poisons the class (NoClassDefFoundError on
 * every later use), which is why there is deliberately no lazy fallback and
 * no post-freeze re-registration path.
 */
public final class CraftmineLootContextParamSets {
    private CraftmineLootContextParamSets() {
    }

    public static final ContextKeySet HUB_REWARD = register("hub_reward", builder -> builder.required(LootContextParams.ORIGIN));

    private static ContextKeySet register(String name, Consumer<ContextKeySet.Builder> consumer) {
        ResourceKey<ContextKeySet> key = ResourceKey.create(Registries.CONTEXT_KEY_SET, Identifier.withDefaultNamespace(name));
        ContextKeySet.Builder builder = new ContextKeySet.Builder();
        consumer.accept(builder);
        return Registry.register(BuiltInRegistries.CONTEXT_KEY_SET, key, builder.build());
    }

    /**
     * Pre-freeze hook: called from CraftmineBootstrap.bootstrapAll (inside
     * BuiltInRegistries.bootStrap, before the freeze() invocation). Invoking
     * any member of this class forces the static initializer, which performs
     * the registration while the registry is still writable - the method body
     * itself stays empty.
     */
    public static void bootstrap() {
    }
}
