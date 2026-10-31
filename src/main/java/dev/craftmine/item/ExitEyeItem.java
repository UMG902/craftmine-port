package dev.craftmine.item;

import dev.craftmine.registry.CraftmineTriggers;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import dev.craftmine.unlock.PlayerUnlocks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import dev.craftmine.duck.CraftmineDuckBridge;

public class ExitEyeItem
extends Item {
    public ExitEyeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
        return 0;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack itemStack = player.getItemInHand(interactionHand);
        player.startUsingItem(interactionHand);
        if (level instanceof ServerLevel) {
            Object object;
            ServerLevel serverLevel = (ServerLevel)level;
            if (player instanceof ServerPlayer && ((dev.craftmine.duck.CraftminePlayerDuck)(Object)((ServerPlayer)(object = (ServerPlayer)player))).isRevisiting() && ((dev.craftmine.duck.CraftmineLevelDuck)(Object)serverLevel).isMine()) {
                CraftmineDuckBridge.level(serverLevel).respawnPlayerIntoHub((ServerPlayer)object, serverPlayer -> serverPlayer.getInventory().clearContent());
                return InteractionResult.SUCCESS_SERVER;
            }
            if (!((dev.craftmine.duck.CraftminePlayerDuck)(Object)player).isUnlocked(PlayerUnlocks.PATHFINDER)) {
                player.hurtServer(serverLevel, player.damageSources().enderPearl(), 2.0f);
            }
            if ((object = serverLevel.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, player.blockPosition(), 100, false)) == null) {
                return InteractionResult.CONSUME;
            }
            EyeOfEnder eyeOfEnder = new EyeOfEnder(level, player.getX(), player.getY(0.5), player.getZ());
            eyeOfEnder.setItem(itemStack);
            eyeOfEnder.signalTo(net.minecraft.world.phys.Vec3.atCenterOf((BlockPos)object));
            level.gameEvent(GameEvent.PROJECTILE_SHOOT, eyeOfEnder.position(), GameEvent.Context.of(player));
            level.addFreshEntity(eyeOfEnder);
            if (player instanceof ServerPlayer) {
                ServerPlayer serverPlayer2 = (ServerPlayer)player;
                CraftmineTriggers.USED_EXIT_EYE.trigger(serverPlayer2, (BlockPos)object);
            }
            float f = Mth.lerp(level.random.nextFloat(), 0.33f, 0.5f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.NEUTRAL, 1.0f, f);
            itemStack.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}

