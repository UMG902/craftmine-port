package dev.craftmine.block;

import com.mojang.serialization.MapCodec;

import dev.craftmine.registry.CraftmineContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The shimmering door: hub corridor doors that open into room menus (see
 * {@link dev.craftmine.inventory.DoorMenu}). Deliberately does NOT open as a door - using it shows
 * the fork's "say the thing" rejection message instead - and offering it a
 * trial key mocks the player with a No Medal trophy. Rendered INVISIBLE:
 * its quads are tessellated into the client sky-window pass.
 */
public class ShimmeringDoorBlock extends DoorBlock {
    private static final MutableComponent REJECT = Component.translatable("door.say_the_thing");

    public ShimmeringDoorBlock(BlockSetType blockSetType, BlockBehaviour.Properties properties) {
        super(blockSetType, properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (itemStack.is(Items.TRIAL_KEY) || itemStack.is(Items.OMINOUS_TRIAL_KEY)) {
            player.getInventory().add(TrophyBlock.createTrophy(TrophyType.NO_MEDAL));
            return InteractionResult.FAIL;
        }
        return super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
        // Fork dxs: player.displayClientMessage(REJECT, true) - action bar on BOTH
        // sides, never chat. The old port code sent chat (sendSystemMessage) on the
        // client side AND a raw action-bar packet on the server side, so the message
        // appeared twice (chat + action bar). sendOverlayMessage is 26.3's rename of
        // displayClientMessage(msg, true) and handles both sides correctly.
        player.sendOverlayMessage(REJECT);
        return InteractionResult.PASS;
    }

    @Override
    protected RenderShape getRenderShape(BlockState blockState) {
        // Drawn exclusively by the client-side SkyWindowRenderer (the snapshot's
        // sky_block pass): the door's model quads are tessellated with vanilla's
        // ModelBlockRenderer and drawn in the custom window pass. Returning
        // INVISIBLE keeps both the vanilla and Sodium chunk meshers out of the
        // way (no double geometry, no z-fighting with the window surface).
        return RenderShape.INVISIBLE;
    }
}
