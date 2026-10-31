/*
 * Mixin: craftmine additions to ServerPlayerGameMode (ports the
 * 25w14craftmine UnlockCondition.onBlockBreak / onUsedItem dispatches).
 *
 * Fork call sites: destroyBlock (inside `if (changed)`, passing the ORIGINAL
 * pre-removal state captured at method head), useItem (inside the
 * InteractionResult.Success branch).
 *
 * TASK 57 FIX: the original port injected before the Block.destroy INVOKE and
 * read level.getBlockState(pos) there - but 26.3 removes the block first
 * (javap: ServerLevel.removeBlock at offset 127, Block.destroy at 186 in
 * destroyBlock), so the handler dispatched with AIR and every state-checking
 * blockBreak ingredient (snow/sand/terracotta/mushrooms/end stone/bone
 * block/nether ores/deepslate/sulfur/amethyst) was silently dead.  The fork's
 * original state is only an LVT local in 26.3; instead of LocalCapture we
 * inject BEFORE the ServerLevel.removeBlock INVOKE (bytecode-verified owner
 * ServerLevel, single call site), where getBlockState(pos) still returns the
 * original state - same value the fork dispatched with.  Known deviation vs
 * the fork: the fork also gated on `changed` (removeBlock returning true);
 * that only differs on concurrent-removal races.
 * Port addition (documented deviation): useItemOn also fires onUsedItem at
 * the item's own useOn success point (before the 2nd ITEM_USED_ON_BLOCK
 * trigger) - the fork only fired from useItem, but then placing a boat could
 * never unlock ICY ("slippery") or feeding bamboo unlock PANDAS; the
 * already-holding-the-ingredient guard rate-limits the extra fires.
 *
 * TASK 61 FIX (survival-mode usedItem dispatch): all three usedItem hook
 * sites run AFTER the item action, and BoatItem.use calls
 * ItemStack.consume(1, player) (javap BoatItem.use offset 243).  consume()
 * is a no-op when player.hasInfiniteMaterials() (javap ItemStack.consume:
 * hasInfiniteMaterials -> return, else shrink) - so in CREATIVE the stack
 * is still a boat at hook time (worked), but in SURVIVAL the held stack is
 * already EMPTY when the hook reads it: ItemStack.getItem()/is() treat
 * empty stacks as AIR, is(BOATS) is false, no ingredient ever dropped and
 * the dispatch probe line never printed.  Fix: capture itemStack.copy()
 * at the HEAD of useItem/useItemOn (before any consumption) into a
 * ThreadLocal and dispatch with that pre-action snapshot; fall back to the
 * mutated parameter only if no snapshot exists.  Capture overwrites on
 * every entry and is taken-and-cleared at dispatch, so no state leaks
 * across clicks; each click takes exactly one dispatch site.
 */
package dev.craftmine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import dev.craftmine.unlock.UnlockCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow
    protected ServerPlayer player;
    @Shadow
    protected ServerLevel level;

    // Task 61: pre-action snapshot of the held stack (see class doc).
    @Unique
    private static final ThreadLocal<ItemStack> craftmine$PRE_USE_STACK = new ThreadLocal<>();

    @Unique
    private ItemStack craftmine$takePreUseStack(ItemStack mutatedStack) {
        ItemStack captured = craftmine$PRE_USE_STACK.get();
        craftmine$PRE_USE_STACK.set(null);
        return captured != null ? captured : mutatedStack;
    }

    @Inject(method = "useItem", at = @At("HEAD"))
    private void craftmine$capturePreUseItem(ServerPlayer serverPlayer, Level level, ItemStack itemStack, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        craftmine$PRE_USE_STACK.set(itemStack.copy());
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void craftmine$capturePreUseItemOn(ServerPlayer serverPlayer, Level level, ItemStack itemStack, InteractionHand interactionHand, net.minecraft.world.phys.BlockHitResult blockHitResult, CallbackInfoReturnable<InteractionResult> cir) {
        craftmine$PRE_USE_STACK.set(itemStack.copy());
    }

    @Inject(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z", shift = At.Shift.BEFORE))
    private void craftmine$onBlockBreak(BlockPos blockPos, CallbackInfoReturnable<Boolean> cir) {
        // Block still present here: getBlockState returns the ORIGINAL state,
        // exactly what the fork passed (it captured the state before removal).
        UnlockCondition.onBlockBreak((Level)this.level, this.player, blockPos, this.level.getBlockState(blockPos));
    }

    @Inject(method = "useItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionResult$Success;heldItemTransformedTo()Lnet/minecraft/world/item/ItemStack;", shift = At.Shift.BEFORE))
    private void craftmine$onUsedItem(ServerPlayer serverPlayer, Level level, ItemStack itemStack, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        // Task 61: dispatch with the PRE-action snapshot - the live stack was
        // already consumed by BoatItem.use in survival (empty -> is(BOATS)
        // false).  See class doc TASK 61 FIX.
        ItemStack preUseStack = craftmine$takePreUseStack(itemStack);
        if (preUseStack.is(net.minecraft.tags.ItemTags.BOATS)) {
            com.mojang.logging.LogUtils.getLogger().info("[craftmine] usedItem dispatch fired: boat item {} in {}", preUseStack.getItem(), level.dimension().identifier());
        }
        UnlockCondition.onUsedItem(level, serverPlayer, preUseStack);
    }

    @Inject(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/triggers/ItemUsedOnLocationTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemInstance;)V", ordinal = 0, shift = At.Shift.BEFORE))
    private void craftmine$onUsedItemOnBlockState(ServerPlayer serverPlayer, Level level, ItemStack itemStack, InteractionHand interactionHand, net.minecraft.world.phys.BlockHitResult blockHitResult, CallbackInfoReturnable<InteractionResult> cir) {
        // Task 60 (bugfix): 26.3's useItemOn has TWO ITEM_USED_ON_BLOCK trigger
        // sites. Ordinal 0 (BlockState.useItemOn branch, javap offset 259) is the
        // one BOATS ON WATER take - the BlockState.useItemOn call consumes the
        // action (BoatItem.use spawns the boat) and returns at 264. Ordinal 1
        // (itemStack.useOn branch, offset 456) only covers placement via the
        // generic UseOnContext path (e.g. boat pushed against land). Task 54
        // wired only ordinal 1, so placing a boat in water never dispatched
        // usedItem and the ICY ingredient never unlocked. Each click takes
        // exactly one branch, so firing on both cannot double-dispatch.
        // Task 61: dispatch with the PRE-action snapshot (survival consumption).
        ItemStack preUseStack0 = craftmine$takePreUseStack(itemStack);
        if (preUseStack0.is(net.minecraft.tags.ItemTags.BOATS)) {
            com.mojang.logging.LogUtils.getLogger().info("[craftmine] usedItemOn(ordinal0) dispatch fired: boat item {} in {}", preUseStack0.getItem(), level.dimension().identifier());
        }
        UnlockCondition.onUsedItem(level, serverPlayer, preUseStack0);
    }

    @Inject(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/triggers/ItemUsedOnLocationTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemInstance;)V", ordinal = 1, shift = At.Shift.BEFORE))
    private void craftmine$onUsedItemOn(ServerPlayer serverPlayer, Level level, ItemStack itemStack, InteractionHand interactionHand, net.minecraft.world.phys.BlockHitResult blockHitResult, CallbackInfoReturnable<InteractionResult> cir) {
        // Task 61: dispatch with the PRE-action snapshot (survival consumption).
        ItemStack preUseStack1 = craftmine$takePreUseStack(itemStack);
        if (preUseStack1.is(net.minecraft.tags.ItemTags.BOATS)) {
            com.mojang.logging.LogUtils.getLogger().info("[craftmine] usedItemOn(ordinal1) dispatch fired: boat item {} in {}", preUseStack1.getItem(), level.dimension().identifier());
        }
        UnlockCondition.onUsedItem(level, serverPlayer, preUseStack1);
    }
}
