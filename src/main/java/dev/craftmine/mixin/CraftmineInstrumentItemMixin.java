/*
 * FT-8 / warden boss intro - the goat horn starts the show.
 * Fork InstrumentItem.use (before the vanilla instrument handling): inside a
 * WARDEN_BOSS_FIGHT mine, using a goat horn near a warden (16 blocks) that is
 * waiting at stage -1 either starts the show (stage 0) once the crowd has
 * waited >= 820 ticks, or heckles the player. The vanilla instrument play
 * still runs afterwards (fork returned CONSUME/FAIL from its own flow - the
 * play itself is untouched).
 */
package dev.craftmine.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.craftmine.registry.CraftmineMemoryModules;

@Mixin(InstrumentItem.class)
public abstract class CraftmineInstrumentItemMixin {
    @Inject(method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
        at = @At("HEAD"))
    private void craftmine$goatHornStartsShow(Level level, Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(level instanceof ServerLevel serverLevel)
            || !dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, dev.craftmine.mines.WorldEffects.WARDEN_BOSS_FIGHT)) {
            return;
        }
        Warden warden = serverLevel.getNearestEntity(Warden.class, TargetingConditions.forNonCombat(),
            player, player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(16.0));
        if (warden == null || !warden.getBrain().isMemoryValue(CraftmineMemoryModules.ACTING_STAGE, -1)) {
            return;
        }
        if (((dev.craftmine.duck.CraftmineWardenDuck) warden).craftmine$crowdWaitingSoundTimeLeft() >= 820) {
            warden.getBrain().setMemory(CraftmineMemoryModules.ACTING_STAGE, 0);
        } else if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("Why are they trying to start the show now? We will get quiet when it's time!"), true);
        }
    }
}
