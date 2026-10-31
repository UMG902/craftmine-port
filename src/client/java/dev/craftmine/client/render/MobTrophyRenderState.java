package dev.craftmine.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Direction;
import dev.craftmine.item.MobTrophyInfo;
import org.jspecify.annotations.Nullable;

/**
 * Per-frame render data for {@link MobTrophyRenderer}: the trophy's mob
 * info, a freshly extracted render state of that mob (null when the trophy
 * is empty), and the block's facing direction.
 */
public class MobTrophyRenderState extends BlockEntityRenderState {
    public @Nullable MobTrophyInfo trophyInfo;
    public @Nullable EntityRenderState entityRenderState;
    public Direction facing = Direction.NORTH;
}
