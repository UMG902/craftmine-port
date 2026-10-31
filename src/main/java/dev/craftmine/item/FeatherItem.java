package dev.craftmine.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import dev.craftmine.unlock.PlayerUnlocks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FeatherItem
extends Item {
    public FeatherItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack itemStack) {
        return super.getName(itemStack);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
        ServerPlayer serverPlayer;
        if (level instanceof ServerLevel && player instanceof ServerPlayer && ((dev.craftmine.duck.CraftminePlayerDuck)(Object)(serverPlayer = (ServerPlayer)player)).isUnlocked(PlayerUnlocks.WING_GUARDIAN)) {
            int n = 5;
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100));
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200));
        }
        return InteractionResult.SUCCESS;
    }
}

