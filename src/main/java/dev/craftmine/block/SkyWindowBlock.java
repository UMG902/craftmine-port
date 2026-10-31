/*
 * Craftmine port - the sky block.
 *
 * 25w14craftmine registers "minecraft:sky" as a plain TransparentBlock whose
 * quads the snapshot's own section renderer routes into its custom sky_block
 * pass (a "window" surface that shows the frame behind it). On 26.3 the same
 * visual is produced by the client SkyWindowRenderer, which collects these
 * blocks and tessellates them with vanilla's ModelBlockRenderer into its own
 * draw pass - so the chunk meshers (vanilla AND Sodium) must skip the block
 * entirely. RenderShape.INVISIBLE does exactly that in both meshers.
 *
 * Behavior is otherwise the vanilla TransparentBlock (same properties are
 * passed by CraftmineContent; the protected ctor is opened in the access
 * widener).
 */
package dev.craftmine.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.TransparentBlock;

public class SkyWindowBlock extends TransparentBlock {

    public SkyWindowBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState blockState) {
        // Drawn exclusively by the client-side SkyWindowRenderer (the snapshot's
        // sky_block pass): never by the vanilla/Sodium chunk meshers.
        return RenderShape.INVISIBLE;
    }
}
