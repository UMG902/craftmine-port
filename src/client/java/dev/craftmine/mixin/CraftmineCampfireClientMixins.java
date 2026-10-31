/*
 * FT-8 gameplay layer (client) - YOU_ARE_THE_CAMPFIRE visuals.
 *
 *  AvatarRenderStateMixin  - implements the campfire flag on 26.3's entity
 *                           player state.
 *  AvatarRendererMixin     - extracts the flag from the local unlock view,
 *                           exactly where the fork's PlayerRenderer set
 *                           playerRenderState.isCampfirePlayer.
 *  ItemInHandLayerMixin    - third person: fire hands (0.5 scale) inside the
 *                           arm-with-item transform, before the (possibly
 *                           empty) item submit - fork position.
 *  FirstPersonHandsMixin   - first person: fire hands (0.7 scale) before each
 *                           held-item submit and after each bare-hand arm
 *                           render (fork had three sites; 26.3's split
 *                           renderer maps them onto these three hooks).
 *  MultiPlayerGameModeMixin- the client fire pass-through: bare-handed
 *                           campfire players don't attack/progress-break FIRE
 *                           blocks (fork startDestroyBlock lambda guard).
 */
package dev.craftmine.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class CraftmineCampfireClientMixins {
    private CraftmineCampfireClientMixins() {
    }

    @Mixin(AvatarRenderState.class)
    public abstract static class AvatarRenderStateMixin implements dev.craftmine.duck.client.CraftmineAvatarRenderStateDuck {
        private boolean craftmine$campfirePlayer;

        @Override
        public boolean craftmine$isCampfirePlayer() {
            return this.craftmine$campfirePlayer;
        }

        @Override
        public void craftmine$setCampfirePlayer(boolean bl) {
            this.craftmine$campfirePlayer = bl;
        }
    }

    @Mixin(AvatarRenderer.class)
    public abstract static class AvatarRendererMixin {
        @Inject(method = "extractRenderState", at = @At("TAIL"))
        private void craftmine$extractCampfire(net.minecraft.world.entity.Avatar entity, AvatarRenderState avatarRenderState, float f, CallbackInfo ci) {
            ((dev.craftmine.duck.client.CraftmineAvatarRenderStateDuck)avatarRenderState).craftmine$setCampfirePlayer(
                entity instanceof net.minecraft.client.player.AbstractClientPlayer abstractClientPlayer
                    && dev.craftmine.duck.CraftmineDuckBridge.isActive(abstractClientPlayer, dev.craftmine.unlock.PlayerUnlocks.YOU_ARE_THE_CAMPFIRE));
        }
    }

    @Mixin(ItemInHandLayer.class)
    public abstract static class ItemInHandLayerMixin {
        @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", shift = At.Shift.AFTER))
        private void craftmine$fireHandsThirdPerson(net.minecraft.client.renderer.entity.state.ArmedEntityRenderState armedEntityRenderState, net.minecraft.client.renderer.item.ItemStackRenderState itemStackRenderState, ItemStack itemStack, HumanoidArm humanoidArm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, CallbackInfo ci) {
            if (armedEntityRenderState instanceof AvatarRenderState avatarRenderState
                && ((dev.craftmine.duck.client.CraftmineAvatarRenderStateDuck)avatarRenderState).craftmine$isCampfirePlayer()) {
                dev.craftmine.client.render.CraftmineFireHands.render(0.5F, poseStack, submitNodeCollector);
            }
        }
    }

    @Mixin(FirstPersonHandsAndItemsRenderer.class)
    public abstract static class FirstPersonHandsMixin {
        @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V", shift = At.Shift.AFTER))
        private void craftmine$fireHandsFirstPerson(net.minecraft.client.renderer.state.level.PlayerRenderState playerRenderState, net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState firstPersonHandsAndItemsRenderState, float f, float g, InteractionHand interactionHand, float h, ItemStack itemStack, float i, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int j, CallbackInfo ci) {
            if (playerRenderState.avatarRenderState != null && ((dev.craftmine.duck.client.CraftmineAvatarRenderStateDuck)playerRenderState.avatarRenderState).craftmine$isCampfirePlayer()) {
                poseStack.pushPose();
                poseStack.translate(0.1F, -0.2F, 0.2F);
                poseStack.rotateDegrees(com.mojang.math.Axis.XP, 90.0F);
                dev.craftmine.client.render.CraftmineFireHands.render(0.7F, poseStack, submitNodeCollector);
                poseStack.popPose();
            }
        }

        @Inject(method = "renderPlayerArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;renderPlayerHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;)V", shift = At.Shift.AFTER))
        private void craftmine$fireHandsBareArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f, float g, HumanoidArm humanoidArm, net.minecraft.client.renderer.state.level.PlayerRenderState playerRenderState, CallbackInfo ci) {
            if (playerRenderState.avatarRenderState != null
                && ((dev.craftmine.duck.client.CraftmineAvatarRenderStateDuck)playerRenderState.avatarRenderState).craftmine$isCampfirePlayer()
                && humanoidArm != HumanoidArm.LEFT) {
                poseStack.pushPose();
                poseStack.translate(-0.4F, 0.7F, 0.1F);
                poseStack.rotateDegrees(com.mojang.math.Axis.XP, 90.0F);
                dev.craftmine.client.render.CraftmineFireHands.render(0.7F, poseStack, submitNodeCollector);
                poseStack.popPose();
            }
        }
    }

    @Mixin(MultiPlayerGameMode.class)
    public abstract static class MultiPlayerGameModeMixin {
        @Shadow
        public net.minecraft.client.Minecraft minecraft;

        /*
         * Fork startDestroyBlock lambda: the fire guard skipped both the
         * blockState.attack call and the destroy-progress branch for
         * bare-handed campfire players punching FIRE, while STILL sending
         * the START_DESTROY_BLOCK action packet (fork always returns it).
         *
         * 26.3 note: lambda$startDestroyBlock$1 is the startPrediction
         * PredictiveAction and RETURNS a Packet, so the callback must be a
         * CallbackInfoReturnable<ServerboundPlayerActionPacket> and cancel
         * means "return the packet early" - a plain CallbackInfo fails
         * mixin apply on the client, and returning any non-Packet value
         * would ClassCastException in the woven CHECKCAST.
         */
        @Inject(method = "lambda$startDestroyBlock$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;attack(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"), cancellable = true)
        private void craftmine$campfireFireGuard(BlockState blockState, BlockPos blockPos, Direction direction, int i, CallbackInfoReturnable<ServerboundPlayerActionPacket> cir) {
            LocalPlayer localPlayer = this.minecraft.player;
            if (localPlayer != null
                && blockState.is(Blocks.FIRE)
                && localPlayer.getMainHandItem().isEmpty()
                && dev.craftmine.duck.CraftmineDuckBridge.isActive(localPlayer, dev.craftmine.unlock.PlayerUnlocks.YOU_ARE_THE_CAMPFIRE)) {
                cir.setReturnValue(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, direction, i));
            }
        }
    }
}
