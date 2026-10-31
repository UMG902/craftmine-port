/*
 * Mixin: FT-8 gameplay layer for Player (ports the 25w14craftmine
 * unlock-driven behavior that 26.3 lost).
 *
 * Ported sites (all fork-faithful, verified against the original jar):
 *  - tick HEAD (inside the fork's "ServerLevel && isMine()" gate):
 *      YOU_ARE_THE_CAMPFIRE: cook both hands via campfire recipes
 *        (cookInHand: particles + crackle + 1/100 chance to swap the cooked
 *        item out of the hand), dirt-connoisseur converts held dirt to food,
 *        the dirt-enjoyer cycle drops dirt from the inventory every
 *        600/#unlocks ticks (+1 dirt every 1200 for the connoisseur),
 *      ONE_HP: pins MAX_HEALTH to 1 via a permanent -19 modifier ("one_hp")
 *        while the effect is active, removes it when inactive,
 *      ARROWVERSE: holding a bow inside a mine refills 16 arrows every 600
 *        ticks (nextArrowRegen).
 *  - getDestroySpeed: DIRT_DESTROYER_1/2 add +10 mining speed each on dirt
 *    when punching with an empty or dirt main hand.
 *  - attack: YOU_ARE_THE_CAMPFIRE ignites the target for 5s when punching
 *    bare-handed; SHIELD_BASH adds +3 base damage while holding a shield and
 *    applies Slowness+Weakness (amplifier 126, 40 ticks) + the shield-block
 *    sound on every landed hit.
 *  - onEnchantmentPerformed: EFFICIENT_ENCHANTING skips the level cost 50%
 *    of the time.
 */
package dev.craftmine.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import dev.craftmine.mines.WorldEffects;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class CraftminePlayerMixin {
    @Unique
    private int craftmine$nextArrowRegen = 0;
    @Unique
    private RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> craftmine$campfireRecipeCache;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$initFields(Level level, com.mojang.authlib.GameProfile gameProfile, CallbackInfo ci) {
        // @Unique field initializers do not merge at JAVA_25 (project gotcha)
        this.craftmine$nextArrowRegen = 0;
        this.craftmine$campfireRecipeCache = RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);
    }

    @Unique
    private static boolean craftmine$isActive(Player player, Holder<dev.craftmine.unlock.PlayerUnlock> holder) {
        return dev.craftmine.duck.CraftmineDuckBridge.isActive(player, holder);
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void craftmine$mineTickCluster(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        Level level = self.level();
        if (!(level instanceof ServerLevel serverLevel) || !(level instanceof dev.craftmine.duck.CraftmineLevelDuck duck) || !duck.isMine()) {
            return;
        }

        if (craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.YOU_ARE_THE_CAMPFIRE)) {
            boolean cooked = craftmine$cookInHand(serverLevel, self.getMainHandItem());
            cooked |= craftmine$cookInHand(serverLevel, self.getOffhandItem());
            if (!cooked && self.getRandom().nextInt(10) == 0) {
                Vec3 eye = self.getEyePosition();
                serverLevel.playSound(null, eye.x, eye.y, eye.z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.1F + self.getRandom().nextFloat() * 0.5F, self.getRandom().nextFloat() * 0.7F + 0.6F);
            }
        }

        if (craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.DIRT_CONNOISSEUR)
            && self.getMainHandItem().is(ItemTags.DIRT) && !self.getMainHandItem().has(DataComponents.FOOD)) {
            self.getMainHandItem().set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).build());
            self.getMainHandItem().set(DataComponents.CONSUMABLE, Consumables.defaultFood().build());
        }

        int dirtLovers = 0;
        dirtLovers += craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.DIRT_ENJOYER_1) ? 1 : 0;
        dirtLovers += craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.DIRT_ENJOYER_2) ? 1 : 0;
        dirtLovers += craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.DIRT_ENJOYER_3) ? 1 : 0;
        dirtLovers += craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.DIRT_DESTROYER_1) ? 1 : 0;
        dirtLovers += craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.DIRT_DESTROYER_2) ? 1 : 0;
        dirtLovers += craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.DIRT_CONNOISSEUR) ? 1 : 0;
        if (dirtLovers > 0 && self.getRandom().nextInt(600 / dirtLovers) == 0) {
            for (ItemStack stack : self.getInventory().getNonEquipmentItems()) {
                if (stack.is(ItemTags.DIRT)) {
                    self.drop(stack.copyWithCount(1), false, net.minecraft.util.Prediction.SERVER_ONLY);
                    stack.setCount(stack.getCount() - 1);
                    break;
                }
            }
        }

        if (craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.DIRT_CONNOISSEUR) && self.getRandom().nextInt(1200) == 0) {
            self.drop(Items.DIRT.getDefaultInstance().copyWithCount(1), false, net.minecraft.util.Prediction.SERVER_ONLY);
        }

        AttributeInstance maxHealth = self.getAttribute(Attributes.MAX_HEALTH);
        if (self.isAlive() && self.getHealth() > 1.0F && !self.isSpectator()
            && dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, WorldEffects.ONE_HP)) {
            maxHealth.addPermanentModifier(new AttributeModifier(Identifier.withDefaultNamespace("one_hp"), -19.0, AttributeModifier.Operation.ADD_VALUE));
            self.setHealth(1.0F);
        } else if (!dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, WorldEffects.ONE_HP)) {
            maxHealth.removeModifier(Identifier.withDefaultNamespace("one_hp"));
        }

        if (craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.ARROWVERSE)
            && self.isHolding(Items.BOW)
            && self instanceof ServerPlayer serverPlayer) {
            this.craftmine$nextArrowRegen--;
            if (this.craftmine$nextArrowRegen < 0) {
                ItemStack arrow = Items.ARROW.getDefaultInstance();
                int slot = serverPlayer.getInventory().findSlotMatchingItem(arrow);
                if (slot == -1) {
                    if (serverPlayer.getInventory().getFreeSlot() != -1) {
                        this.craftmine$nextArrowRegen = 600;
                        serverPlayer.getInventory().add(arrow.copyWithCount(16));
                    }
                } else {
                    ItemStack existing = serverPlayer.getInventory().getItem(slot);
                    int canAdd = Math.min(arrow.getMaxStackSize() - existing.getCount(), 16);
                    if (canAdd > 0) {
                        this.craftmine$nextArrowRegen = 600;
                        serverPlayer.getInventory().add(arrow.copyWithCount(canAdd));
                    }
                }
            }
        }
    }

    @Unique
    private boolean craftmine$cookInHand(ServerLevel serverLevel, ItemStack stack) {
        Player self = (Player) (Object) this;
        SingleRecipeInput input = new SingleRecipeInput(stack);
        ItemStack result = this.craftmine$campfireRecipeCache.getRecipeFor(input, serverLevel)
            .map(recipe -> recipe.value().assemble(input))
            .orElse(stack);
        if (result.isItemEnabled(((Player)(Object)this).level().enabledFeatures()) && result != stack) {
            craftmine$cookParticleTick(serverLevel, self, stack);
            Vec3 eye = self.getEyePosition();
            if (self.getRandom().nextInt(10) == 0) {
                serverLevel.playSound(null, eye.x, eye.y, eye.z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.5F + self.getRandom().nextFloat() * 0.5F, self.getRandom().nextFloat() * 0.7F + 0.6F);
            }
            if (self.getRandom().nextInt(100) != 0) {
                return true;
            }
            self.drop(result.copyWithCount(1), true, net.minecraft.util.Prediction.SERVER_ONLY);
            serverLevel.playSound(null, eye.x, eye.y, eye.z, SoundEvents.GENERIC_BURN, SoundSource.BLOCKS, 1.5F, self.getRandom().nextFloat() * 0.7F + 1.0F);
            stack.setCount(stack.getCount() - 1);
            return true;
        }
        return false;
    }

    @Unique
    private static void craftmine$cookParticleTick(ServerLevel serverLevel, Player player, ItemStack stack) {
        RandomSource random = serverLevel.random;
        // inline of vanilla 1.21.5 Entity.getHandHoldingItemAngle (dropped in 26.3)
        boolean offhandOnly = player.getOffhandItem().is(stack.getItem()) && !player.getMainHandItem().is(stack.getItem());
        net.minecraft.world.entity.HumanoidArm arm = offhandOnly ? player.getMainArm().getOpposite() : player.getMainArm();
        Vec3 handAngle = player.calculateViewVector(0.0F, player.getYRot() + (float)(arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 80 : -80)).scale(0.5);
        Vec3 particlePos = player.getEyePosition().add(handAngle.scale(0.8)).add(player.getLookAngle().scale(0.2));
        if (random.nextFloat() < 0.15F) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, particlePos.x, particlePos.y - 0.5, particlePos.z, 1, 0.1, 0.0, 0.1, 0.02);
        }
        if (random.nextFloat() < 0.05F) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, particlePos.x, particlePos.y - 0.5, particlePos.z, 1, 0.1, 0.0, 0.1, 0.02);
        }
        if (random.nextFloat() < 0.03F) {
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, particlePos.x, particlePos.y - 0.5, particlePos.z, 1, 0.1, 0.0, 0.1, 0.02);
        }
    }

    /*
     * DIRT_DESTROYER_1/2: fork inlines "+10 per level while punching dirt
     * bare-handed or with a dirt item" into getDestroySpeed AFTER the
     * mining-efficiency add and BEFORE the haste/fatigue/block-break-speed
     * multipliers. 26.3 has no such call site, so we modify the speed local
     * directly at the first instruction after that point (the hasDigSpeed
     * probe). ordinal 0 = first float local = the running speed value.
     */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(
        method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffectUtil;hasDigSpeed(Lnet/minecraft/world/entity/LivingEntity;)Z"),
        ordinal = 0)
    private float craftmine$dirtDestroyer(float value, net.minecraft.world.level.block.state.BlockState state) {
        Player self = (Player) (Object) this;
        if (state.is(BlockTags.DIRT) && (self.getMainHandItem().isEmpty() || self.getMainHandItem().is(ItemTags.DIRT))) {
            if (craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.DIRT_DESTROYER_1)) {
                value += 10.0F;
            }
            if (craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.DIRT_DESTROYER_2)) {
                value += 10.0F;
            }
        }
        return value;
    }

    /*
     * YOU_ARE_THE_CAMPFIRE (server combat part): fork ignited the target for
     * 5s right after the attack damage source was resolved, when punching
     * bare-handed.
     */
    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;createAttackSource(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/damagesource/DamageSource;", shift = At.Shift.AFTER))
    private void craftmine$campfireIgnite(net.minecraft.world.entity.Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.getWeaponItem().isEmpty() && craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.YOU_ARE_THE_CAMPFIRE)) {
            target.igniteForSeconds(5.0F);
        }
    }

    /*
     * SHIELD_BASH: fork added +3.0F to the base attack damage (before the
     * attack-strength scaling) when the weapon is a shield.
     */
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
        method = "attack(Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double craftmine$shieldBashDamage(double original) {
        Player self = (Player) (Object) this;
        if (craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.SHIELD_BASH) && self.getWeaponItem().is(Items.SHIELD)) {
            return original + 3.0;
        }
        return original;
    }

    /*
     * SHIELD_BASH on-hit: fork applied Slowness+Weakness (40 ticks, amplifier
     * 126) + the shield-block sound to every LivingEntity hit while bashing.
     */
    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setLastHurtMob(Lnet/minecraft/world/entity/Entity;)V"))
    private void craftmine$shieldBashOnHit(net.minecraft.world.entity.Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (target instanceof LivingEntity livingTarget
            && craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.SHIELD_BASH)
            && self.getWeaponItem().is(Items.SHIELD)) {
            livingTarget.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 40, 126), self);
            livingTarget.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 40, 126), self);
            self.level().playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.SHIELD_BLOCK, self.getSoundSource(), 1.0F, 1.0F);
        }
    }

    /*
     * EFFICIENT_ENCHANTING: fork's onEnchantmentPerformed skipped the level
     * consumption 50% of the time while the unlock is active.
     */
    @Inject(method = "onEnchantmentPerformed(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"), cancellable = true)
    private void craftmine$efficientEnchanting(ItemStack itemStack, int cost, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (craftmine$isActive(self, dev.craftmine.unlock.PlayerUnlocks.EFFICIENT_ENCHANTING) && self.getRandom().nextBoolean()) {
            ci.cancel();
        }
    }
}
