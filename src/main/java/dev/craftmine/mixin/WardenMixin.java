/*
 * Mixin: craftmine additions to Warden (ported from the 25w14craftmine Warden diff).
 * Adds the acting-sequence timing fields and stopActing() via the duck interface.
 */
package dev.craftmine.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import dev.craftmine.duck.CraftmineWardenDuck;
import dev.craftmine.registry.CraftmineMemoryModules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.Entity;

@Mixin(Warden.class)
public abstract class WardenMixin extends Monster implements CraftmineWardenDuck {
    @org.spongepowered.asm.mixin.Shadow
    public abstract net.minecraft.world.entity.monster.warden.AngerManagement getAngerManagement();

    @org.spongepowered.asm.mixin.Shadow
    private boolean isDiggingOrEmerging() {
        throw new AssertionError("mixin shadow");
    }

    protected WardenMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        throw new AssertionError("mixin ctor");
    }

    @Unique
    public int craftmine$crowdWaitingSoundTimeLeft = 0;
    @Unique
    public int craftmine$timeUntilGoathornHint = 1000;
    @Unique
    public int craftmine$timeUntilDiscShouldPlay = 400;
    @Unique
    public int craftmine$timeUntilLightShouldTurnOn = 200;
    @Unique
    public int craftmine$timeUntilMobHead = 200;
    @Unique
    public int craftmine$timeUntilRockets = 200;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$initTimingFields(net.minecraft.world.entity.EntityType entityType, Level level, CallbackInfo ci) {
        // @Unique field initializers do not merge at JAVA_25 (project gotcha)
        this.craftmine$crowdWaitingSoundTimeLeft = 0;
        this.craftmine$timeUntilGoathornHint = 1000;
        this.craftmine$timeUntilDiscShouldPlay = 400;
        this.craftmine$timeUntilLightShouldTurnOn = 200;
        this.craftmine$timeUntilMobHead = 200;
        this.craftmine$timeUntilRockets = 200;
    }

    /*
     * FT-8 / warden boss intro (fork Warden.tick server block): while the
     * WARDEN_BOSS_FIGHT effect is active, run the show timeline - decrement
     * the stage timers and fire the one-shot "waiting for the show" hint
     * messages through the first player.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void craftmine$bossShowTimeline(CallbackInfo ci) {
        Warden self = (Warden) (Object) this;
        if (!(self.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        boolean boss = dev.craftmine.server.CraftmineGameplayEffects.isActive(serverLevel, dev.craftmine.mines.WorldEffects.WARDEN_BOSS_FIGHT);
        if (self.level().isClientSide()) {
            if (boss) {
                this.craftmine$crowdWaitingSoundTimeLeft++;
            }
            return;
        }
        if (!boss) {
            return;
        }
        Integer stage = self.getBrain().getMemory(CraftmineMemoryModules.ACTING_STAGE).orElse(null);
        if (stage != null && stage == -1) {
            this.craftmine$timeUntilGoathornHint--;
            this.craftmine$crowdWaitingSoundTimeLeft++;
        }
        if (stage != null && stage == 0) {
            this.craftmine$timeUntilDiscShouldPlay--;
        }
        if (stage != null && stage == 1) {
            this.craftmine$timeUntilLightShouldTurnOn--;
        }
        if (stage != null && stage == 2) {
            this.craftmine$timeUntilMobHead--;
        }
        if (stage != null && stage == 3) {
            this.craftmine$timeUntilMobHead = this.craftmine$timeUntilRockets - 1;
        }
        if (this.craftmine$timeUntilGoathornHint == 0 && !serverLevel.players().isEmpty()) {
            this.craftmine$timeUntilGoathornHint = -1;
            serverLevel.players().get(0).sendSystemMessage(
                net.minecraft.network.chat.Component.literal("I wonder when they will \"Go-at\" (horn) the start of the fight?"), true);
        }
        if (this.craftmine$timeUntilDiscShouldPlay == 0 && !serverLevel.players().isEmpty()) {
            this.craftmine$timeUntilDiscShouldPlay = -1;
            serverLevel.players().get(0).sendSystemMessage(
                net.minecraft.network.chat.Component.literal("Where is the music? Isn't it supposed to start?"), true);
        }
        if (this.craftmine$timeUntilLightShouldTurnOn == 0 && !serverLevel.players().isEmpty()) {
            this.craftmine$timeUntilLightShouldTurnOn = -1;
            serverLevel.players().get(0).sendSystemMessage(
                net.minecraft.network.chat.Component.literal("The Warden is so ready to act, when does the lightshow start?"), true);
        }
        if (this.craftmine$timeUntilMobHead == 0 && !serverLevel.players().isEmpty()) {
            this.craftmine$timeUntilMobHead = -1;
            serverLevel.players().get(0).sendSystemMessage(
                net.minecraft.network.chat.Component.literal("We need another mob on stage! (Or a player who looks like a mob)"), true);
        }
        if (this.craftmine$timeUntilRockets == 0 && !serverLevel.players().isEmpty()) {
            this.craftmine$timeUntilRockets = -1;
            serverLevel.players().get(0).sendSystemMessage(
                net.minecraft.network.chat.Component.literal("It looks like the Warden is done, where is the finale?"), true);
        }
    }

    /*
     * FT-8 / warden boss intro (fork Warden.getAmbientSound): a waiting
     * warden (stage -1, crowd timer still at 0) plays the crowd-waiting
     * ambience once from the west side of the arena.
     */
    @Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
    private void craftmine$crowdWaitingAmbient(CallbackInfoReturnable<net.minecraft.sounds.SoundEvent> cir) {
        Warden self = (Warden) (Object) this;
        if (self.hasPose(Pose.ROARING) || this.isDiggingOrEmerging()) {
            return;
        }
        Integer stage = self.getBrain().getMemory(CraftmineMemoryModules.ACTING_STAGE).orElse(null);
        if (stage != null && stage == -1 && this.craftmine$crowdWaitingSoundTimeLeft == 0) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(self.position()).offset(-8, 0, 0);
            self.level().playSound(null, pos, dev.craftmine.registry.CraftmineSoundEvents.VILLAGER_CROWD_WAITING, net.minecraft.sounds.SoundSource.MASTER, 100.0F, 1.0F);
        }
    }

    /*
     * FT-8 / warden boss intro (fork Warden.hurtServer first line): damage
     * interrupts the show - stopActing() drops the stage, targets the attacker
     * and swings the brain back into FIGHT.
     */
    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void craftmine$stopActingOnHurt(net.minecraft.server.level.ServerLevel serverLevel, net.minecraft.world.damagesource.DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> cir) {
        if (this.getBrain().isActive(dev.craftmine.registry.CraftmineActivities.ACTING)) {
            this.stopActing();
        }
    }

    /*
     * FT-8 / warden boss intro (fork Warden.finalizeSpawn): wardens spawning
     * inside a boss-fight mine start the show at stage -1 (waiting for the
     * goat horn) instead of the vanilla emerging behaviour.
     */
    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void craftmine$bossSpawnStage(net.minecraft.world.level.ServerLevelAccessor serverLevelAccessor, net.minecraft.world.DifficultyInstance difficultyInstance, net.minecraft.world.entity.EntitySpawnReason entitySpawnReason, net.minecraft.world.entity.SpawnGroupData spawnGroupData, CallbackInfoReturnable<net.minecraft.world.entity.SpawnGroupData> cir) {
        Warden self = (Warden) (Object) this;
        if (dev.craftmine.server.CraftmineGameplayEffects.isActive(self.level(), dev.craftmine.mines.WorldEffects.WARDEN_BOSS_FIGHT)) {
            this.getBrain().setMemory(CraftmineMemoryModules.ACTING_STAGE, -1);
        }
    }

    @Override
    public void stopActing() {
        this.setPose(Pose.STANDING);
        this.getBrain().eraseMemory(CraftmineMemoryModules.ACTING_STAGE);
        this.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        this.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER).ifPresent(player -> {
            this.getAngerManagement().increaseAnger((Entity)player, 150);
            this.setTarget((LivingEntity)player);
        });
        this.getBrain().setActiveActivityIfPossible(Activity.FIGHT);
    }

    @Override
    public int craftmine$crowdWaitingSoundTimeLeft() {
        return this.craftmine$crowdWaitingSoundTimeLeft;
    }

    @Override
    public void craftmine$setCrowdWaitingSoundTimeLeft(int value) {
        this.craftmine$crowdWaitingSoundTimeLeft = value;
    }

    @Override
    public int craftmine$timeUntilGoathornHint() {
        return this.craftmine$timeUntilGoathornHint;
    }

    @Override
    public void craftmine$setTimeUntilGoathornHint(int value) {
        this.craftmine$timeUntilGoathornHint = value;
    }

    @Override
    public int craftmine$timeUntilDiscShouldPlay() {
        return this.craftmine$timeUntilDiscShouldPlay;
    }

    @Override
    public void craftmine$setTimeUntilDiscShouldPlay(int value) {
        this.craftmine$timeUntilDiscShouldPlay = value;
    }

    @Override
    public int craftmine$timeUntilLightShouldTurnOn() {
        return this.craftmine$timeUntilLightShouldTurnOn;
    }

    @Override
    public void craftmine$setTimeUntilLightShouldTurnOn(int value) {
        this.craftmine$timeUntilLightShouldTurnOn = value;
    }

    @Override
    public int craftmine$timeUntilMobHead() {
        return this.craftmine$timeUntilMobHead;
    }

    @Override
    public void craftmine$setTimeUntilMobHead(int value) {
        this.craftmine$timeUntilMobHead = value;
    }

    @Override
    public int craftmine$timeUntilRockets() {
        return this.craftmine$timeUntilRockets;
    }

    @Override
    public void craftmine$setTimeUntilRockets(int value) {
        this.craftmine$timeUntilRockets = value;
    }
}
