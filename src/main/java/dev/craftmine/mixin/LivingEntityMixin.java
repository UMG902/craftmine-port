/*
 * Mixin: craftmine additions to LivingEntity (restores the 25w14craftmine
 * universal trophy-drop hook lost in the 26.3 base).
 *
 * The fork's LivingEntity.dropAllDeathLoot ended with a generic hook: any
 * LivingEntity killed by a player has a 10% chance to drop its mob trophy
 * (MobTrophyBlock.generateForMob: weighted grade grass/stone/gold/diamond/
 * netherite + 1% shimmering) - that is how EVERY mob, including 26.3's new
 * ones, became a trophy source. The 26.3 base has no such call, leaving the
 * port's MobTrophyBlock.generateForMob with zero callers (nothing ever drops
 * a trophy). Re-added here at the same point (method tail, after all vanilla
 * drops), fork-faithful: LivingEntity scope means players keep dropping
 * player trophies too (26.3's ServerPlayer does not override
 * dropAllDeathLoot). Babies and mobGriefing-style gamerules do not gate the
 * fork's hook either - kept as-is.
 */
package dev.craftmine.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import dev.craftmine.block.MobTrophyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    protected int lastHurtByPlayerMemoryTime;

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("TAIL"))
    private void craftmine$dropMobTrophy(ServerLevel serverLevel, DamageSource damageSource, CallbackInfo ci) {
        // mixin classes do not inherit target methods for javac - go through Entity explicitly
        Entity entity = (Entity)(Object)this;
        if (this.lastHurtByPlayerMemoryTime > 0 && entity.getRandom().nextFloat() < 0.1F) {
            entity.spawnAtLocation(serverLevel, MobTrophyBlock.generateForMob(entity.typeHolder(), entity.getRandom()));
        }
    }

    /*
     * Task 54: ports the fork's UnlockCondition.onPlayerKilledEntity dispatch
     * (fork: LivingEntity.die resolved the kill credit and fired the dispatch
     * when the killer is a ServerPlayer - indirect kills via pets/projectiles
     * count). 26.3's ServerPlayer.die does NOT call super.die, so players'
     * own deaths stay out of this path (fork-identical). Fires before
     * dropAllDeathLoot, i.e. inside the ServerLevel + killedEntity-success
     * guard of die().
     */
    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", shift = At.Shift.BEFORE))
    private void craftmine$onPlayerKilledEntityDispatch(net.minecraft.world.damagesource.DamageSource damageSource, CallbackInfo ci) {
        LivingEntity livingEntity = (LivingEntity) (Object) this;
        LivingEntity livingEntity2 = livingEntity.getKillCredit();
        if (livingEntity2 instanceof net.minecraft.server.level.ServerPlayer) {
            dev.craftmine.unlock.UnlockCondition.onPlayerKilledEntity(((net.minecraft.server.level.ServerPlayer)livingEntity2).level(), (net.minecraft.server.level.ServerPlayer)livingEntity2, livingEntity);
        }
    }

    /*
     * FT-8 gameplay layer - WorldEffects.ICY (world effect "icy": every
     * surface becomes as slippery as packed ice).
     *
     * The fork's Block.getFriction(boolean) returned 0.989 while
     * Level.getIsIcy(), and its LivingEntity.travelInAir passed
     * level().getIsIcy() into that call. 26.3's travelInAir reads the plain
     * Block.getFriction(), so we intercept that read: while ICY is active on
     * this level the block friction becomes 0.989 (fork constant), otherwise
     * vanilla. Client and server both run this path (same method both sides).
     */
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
        method = "travelInAir(Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float craftmine$icyFriction(float original) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (dev.craftmine.server.CraftmineGameplayEffects.isIcy(self)) {
            return 0.989F;
        }
        return original;
    }

    /*
     * FT-8 - WorldEffects.NO_DROPS: fork LivingEntity.dropFromLootTable
     * returned early while the level has NO_DROPS active. In 26.3 every
     * loot-table drop (including the special charged-creeper skull call)
     * funnels through the 5-arg dropFromLootTable, so guarding that one
     * entry point reproduces the fork: loot tables silenced, equipment
     * drops (dropCustomDeathLoot) untouched.
     */
    @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private void craftmine$noDropsLootTable(ServerLevel serverLevel, DamageSource damageSource, boolean bl, net.minecraft.resources.ResourceKey resourceKey, java.util.function.Consumer consumer, CallbackInfo ci) {
        if (dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, dev.craftmine.mines.WorldEffects.NO_DROPS)) {
            ci.cancel();
        }
    }

    /*
     * WorldEffects.FISH_OUT_OF_WATER gameplay - the effect was REGISTERED in
     * the port (lang name/hint/description, xpModifier 5x, challenge styling)
     * but its actual hook never carried over: the fork restructures the
     * vanilla air block inside LivingEntity.baseTick so that the PLAYER is
     * the fish out of water - breathes WATER (air regenerates while the eyes
     * are submerged, ungated), drowns in AIR (vanilla decreaseAirSupply +
     * shouldTakeDrowningDamage -> 2.0 drown damage + event 67), bubble
     * columns count as air (no free breath), water-breathing potion /
     * creative invulnerable are exempt, and riding a vehicle that
     * dismounts underwater while actually submerged still forces
     * stopRiding. Server side only (26.3's block sits inside the
     * `level() instanceof ServerLevel` guard, fork-identical).
     *
     * 26.3 wiring (baseTick has exactly one isEyeInFluid, one
     * BlockState.is(Object) bubble check, one dismountsUnderwater and one
     * shouldEffectsRefillAirsupply call - javap-verified):
     * 1. eye wrap: affected players are "treated as eye in water" exactly
     *    when the fork's drown branch applies (!eyeInWater || inBubble).
     * 2. bubble wrap: affected players never read as standing in a bubble
     *    column, so the fused `treated && !bubble` branch condition reduces
     *    1:1 to the fork's `!eyeInWater || inBubble` drown condition; the
     *    outer else-if regen branch is the fork's ungated water regen.
     * 3. dismountsUnderwater wrap: vanilla's dismount sits INSIDE the branch
     *    affected players invert away from (it would dismount dry players in
     *    air and skip actually-submerged ones) - suppress it for them.
     * 4. refill wrap: the inner else-if regen is gated by
     *    MobEffectUtil.shouldEffectsRefillAirsupply (26.3's nautilus-breath
     *    check); the fork's regen is ungated, so affected players refill.
     * 5. TAIL: re-run the vanilla dismount check verbatim for affected
     *    players with the ACTUAL (un-inverted) water/bubble state, which is
     *    exactly the fork's own dismount line inside its effect branch.
     */
    private boolean craftmine$isFishOutOfWater(net.minecraft.world.entity.Entity entity) {
        return entity.level() instanceof ServerLevel serverLevel
            && dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, dev.craftmine.mines.WorldEffects.FISH_OUT_OF_WATER);
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
        method = "baseTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean craftmine$fowEyeInWater(net.minecraft.world.entity.LivingEntity instance, net.minecraft.tags.TagKey tag,
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        boolean eyeInWater = original.call(instance, tag);
        if (tag == net.minecraft.tags.FluidTags.WATER
            && instance instanceof net.minecraft.world.entity.player.Player
            && this.craftmine$isFishOutOfWater(instance)) {
            boolean inBubbleColumn = instance.level().getBlockState(net.minecraft.core.BlockPos.containing(instance.getX(), instance.getEyeY(), instance.getZ()))
                .is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN);
            return !eyeInWater || inBubbleColumn;
        }
        return eyeInWater;
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
        method = "baseTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean craftmine$fowNoBubbleAir(net.minecraft.world.level.block.state.BlockState instance, Object block,
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        boolean isBubbleColumn = original.call(instance, block);
        if (isBubbleColumn && block == net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN
            && (Object) this instanceof net.minecraft.world.entity.player.Player
            && this.craftmine$isFishOutOfWater((net.minecraft.world.entity.Entity) (Object) this)) {
            return false;
        }
        return isBubbleColumn;
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
        method = "baseTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;dismountsUnderwater()Z"))
    private boolean craftmine$fowSuppressVanillaDismount(net.minecraft.world.entity.Entity instance,
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        if ((Object) this instanceof net.minecraft.world.entity.player.Player
            && this.craftmine$isFishOutOfWater((net.minecraft.world.entity.Entity) (Object) this)) {
            return false;
        }
        return original.call(instance);
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
        method = "baseTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffectUtil;shouldEffectsRefillAirsupply(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean craftmine$fowUngatedRefill(net.minecraft.world.entity.LivingEntity instance,
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        if ((Object) this instanceof net.minecraft.world.entity.player.Player
            && this.craftmine$isFishOutOfWater((net.minecraft.world.entity.Entity) (Object) this)) {
            return true;
        }
        return original.call(instance);
    }

    @Inject(method = "baseTick", at = @At("TAIL"))
    private void craftmine$fowUnderwaterDismount(CallbackInfo ci) {
        net.minecraft.world.entity.LivingEntity livingEntity = (net.minecraft.world.entity.LivingEntity) (Object) this;
        if (livingEntity instanceof net.minecraft.world.entity.player.Player
            && this.craftmine$isFishOutOfWater(livingEntity)
            && livingEntity.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
            && !livingEntity.level().getBlockState(net.minecraft.core.BlockPos.containing(livingEntity.getX(), livingEntity.getEyeY(), livingEntity.getZ()))
                .is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN)
            && livingEntity.isPassenger() && livingEntity.getVehicle() != null && livingEntity.getVehicle().dismountsUnderwater()) {
            livingEntity.stopRiding();
        }
    }
}
