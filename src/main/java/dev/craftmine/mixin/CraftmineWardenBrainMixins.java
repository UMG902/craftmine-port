/*
 * FT-8 / warden boss intro - brain wiring.
 *
 *  WardenBrainProviderMixin: 26.3 builds the warden Brain.Provider in
 *  Warden.<clinit> with an explicit memory allow-list; the fork added
 *  ACTING_STAGE to its memory types, so the provider redirect extends that
 *  list (without it Brain.setMemory(ACTING_STAGE) is a no-op).
 *
 *  WardenAiMixin: appends the ACTING ActivityData (priority 10, the six stage
 *  behaviors, condition = stage memory present + no attack target) to
 *  getActivities, and replaces updateActivity's priority list with the fork's
 *  (FIGHT, ACTING, EMERGE, DIG, ROAR, INVESTIGATE, SNIFF, IDLE). For vanilla
 *  wardens (no stage memory) the ACTING activity never activates - the
 *  condition set fails - so this is a no-op outside boss mines.
 */
package dev.craftmine.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenAi;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.craftmine.registry.CraftmineActivities;
import dev.craftmine.registry.CraftmineMemoryModules;

public final class CraftmineWardenBrainMixins {
    private CraftmineWardenBrainMixins() {
    }

    @Mixin(Warden.class)
    public abstract static class WardenBrainProviderMixin {
        @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/entity/ai/Brain;provider(Ljava/util/Collection;Ljava/util/Collection;Lnet/minecraft/world/entity/ai/Brain$ActivitySupplier;)Lnet/minecraft/world/entity/ai/Brain$Provider;"))
        private static Brain.Provider craftmine$addActingStageMemory(java.util.Collection memories, java.util.Collection sensors, Brain.ActivitySupplier activitySupplier) {
            List extended = new ArrayList(memories);
            extended.add(CraftmineMemoryModules.ACTING_STAGE);
            return Brain.provider(extended, sensors, activitySupplier);
        }
    }

    @Mixin(WardenAi.class)
    public abstract static class WardenAiMixin {
        @ModifyReturnValue(method = "getActivities", at = @At("RETURN"))
        private static List craftmine$addActingActivity(List original) {
            List<ActivityData<Warden>> activities = new ArrayList<>(original);
            com.google.common.collect.ImmutableList.Builder<Behavior<? super Warden>> behaviors = com.google.common.collect.ImmutableList.builder();
            behaviors.add(new LookAtTargetSink(45, 90));
            behaviors.add(new MoveToTargetSink(500, 600));
            behaviors.addAll(dev.craftmine.mines.WardenActingStages.actingShow());
            Set<Pair<MemoryModuleType<?>, MemoryStatus>> conditions = com.google.common.collect.ImmutableSet.of(
                Pair.of(CraftmineMemoryModules.ACTING_STAGE, MemoryStatus.VALUE_PRESENT),
                Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT));
            activities.add(ActivityData.create(CraftmineActivities.ACTING, 10, behaviors.build(), conditions));
            return activities;
        }

        @Inject(method = "updateActivity", at = @At("HEAD"), cancellable = true)
        private static void craftmine$forkActivityPriority(Brain brain, CallbackInfo ci) {
            brain.setActiveActivityToFirstValid(com.google.common.collect.ImmutableList.of(
                Activity.FIGHT, CraftmineActivities.ACTING, Activity.EMERGE, Activity.DIG,
                Activity.ROAR, Activity.INVESTIGATE, Activity.SNIFF, Activity.IDLE));
            ci.cancel();
        }
    }
}
