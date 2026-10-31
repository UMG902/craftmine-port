/*
 * FT-8 gameplay layer - FIRE_SHIELD unlock + LODESTONE_EXITS unlock.
 *
 *  ShieldFireMixin   - the fork's ShieldItem.use gave FIRE_RESISTANCE (100
 *                      ticks) to FIRE_SHIELD holders whenever the shield was
 *                      right-clicked on the server. 26.3 shields no longer
 *                      declare use() - Item.use's DataComponents.BLOCKS_ATTACKS
 *                      branch starts the blocking - so the inject hooks that
 *                      exact branch and gates on instanceof ShieldItem.
 *  CompassItemMixin  - LODESTONE_EXITS: a compass with no lodestone tracker
 *                      in the hands/belt of a LODESTONE_EXITS player tracks
 *                      the nearest eye-of-ender-located structure (the mine
 *                      exit), re-resolving every 10 ticks like the fork's
 *                      exits-aware LodestoneTracker.tick did.
 */
package dev.craftmine.mixin;

import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class CraftmineShieldCompassMixins {
    private CraftmineShieldCompassMixins() {
    }

    @Mixin(net.minecraft.world.item.Item.class)
    public abstract static class ShieldFireMixin {
        @Inject(method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;startUsingItem(Lnet/minecraft/world/InteractionHand;)V"))
        private void craftmine$fireShield(Level level, Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
            if ((Object) this instanceof net.minecraft.world.item.ShieldItem
                && level instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer
                && dev.craftmine.duck.CraftmineDuckBridge.isActive(serverPlayer, dev.craftmine.unlock.PlayerUnlocks.FIRE_SHIELD)) {
                serverPlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 100));
            }
        }
    }

    @Mixin(net.minecraft.world.item.CompassItem.class)
    public abstract static class CompassItemMixin {
        @Inject(method = "inventoryTick(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EquipmentSlot;)V", at = @At("TAIL"))
        private void craftmine$lodestoneExits(ItemStack itemStack, ServerLevel serverLevel, Entity entity, net.minecraft.world.entity.EquipmentSlot equipmentSlot, CallbackInfo ci) {
            LodestoneTracker tracker = itemStack.get(DataComponents.LODESTONE_TRACKER);
            if (tracker == null) {
                if (entity instanceof ServerPlayer serverPlayer
                    && dev.craftmine.duck.CraftmineDuckBridge.isActive(serverPlayer, dev.craftmine.unlock.PlayerUnlocks.LODESTONE_EXITS)) {
                    GlobalPos exit = craftmine$findExitTracker(serverPlayer.blockPosition(), serverLevel);
                    if (exit != null) {
                        itemStack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(exit), true));
                    }
                }
            } else if (entity instanceof ServerPlayer serverPlayer
                && dev.craftmine.duck.CraftmineDuckBridge.isActive(serverPlayer, dev.craftmine.unlock.PlayerUnlocks.LODESTONE_EXITS)
                && serverLevel.getRandom().nextInt(10) == 0) {
                // fork LodestoneTracker.tick (exits=true): re-resolve the
                // nearest exit every 10 ticks so the needle tracks the closest one
                GlobalPos exit = craftmine$findExitTracker(serverPlayer.blockPosition(), serverLevel);
                if (exit != null && !exit.equals(tracker.target().orElse(null))) {
                    itemStack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(exit), true));
                }
            }
        }

        private static GlobalPos craftmine$findExitTracker(net.minecraft.core.BlockPos blockPos, ServerLevel serverLevel) {
            net.minecraft.core.BlockPos found = serverLevel.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, blockPos, 100, false);
            return found == null ? null : GlobalPos.of(serverLevel.dimension(), found);
        }
    }
}
