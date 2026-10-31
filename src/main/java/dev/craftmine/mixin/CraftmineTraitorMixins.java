/*
 * FT-8 gameplay layer - traitor villager wiring mixins.
 *
 *  VillagerProfessionMixin - registers the traitor profession at the tail of
 *                            the vanilla VillagerProfession.bootstrap.
 *  GiveGiftToHeroMixin     - adds the traitor entry to the clinit GIFTS map
 *                            (the fork used the weaponsmith gift table),
 *                            same clinit redirect pattern as
 *                            DefaultAttributesMixin.
 */
package dev.craftmine.mixin;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class CraftmineTraitorMixins {
    private CraftmineTraitorMixins() {
    }

    @Mixin(VillagerProfession.class)
    public abstract static class VillagerProfessionMixin {
        @Inject(method = "bootstrap(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/entity/npc/villager/VillagerProfession;",
            at = @At("RETURN"))
        private static void craftmine$registerTraitor(Registry<VillagerProfession> registry, CallbackInfoReturnable<VillagerProfession> cir) {
            dev.craftmine.server.CraftmineTraitorVillager.register(registry);
        }
    }

    @Mixin(GiveGiftToHero.class)
    public abstract static class GiveGiftToHeroMixin {
        /*
         * The handler's ERASED return type must exactly match the redirected
         * build() call's return (ImmutableMap): a java.lang.Object return makes
         * the mixin fail to apply at class-load time with
         * InvalidInjectionException ("Found unexpected return type
         * java.lang.Object, expected com.google.common.collect.ImmutableMap")
         * every time a villager brain is built - the traitor gifts silently
         * vanish and the log fills with errors. Never widen handler returns.
         */
        @Redirect(
            method = "<clinit>",
            at = @At(
                value = "INVOKE",
                target = "Lcom/google/common/collect/ImmutableMap$Builder;build()Lcom/google/common/collect/ImmutableMap;"
            ))
        private static ImmutableMap<Object, Object> craftmine$traitorGift(ImmutableMap.Builder<Object, Object> builder) {
            builder.put(dev.craftmine.server.CraftmineTraitorVillager.TRAITOR,
                net.minecraft.world.level.storage.loot.BuiltInLootTables.WEAPONSMITH_GIFT);
            return builder.build();
        }
    }
}
