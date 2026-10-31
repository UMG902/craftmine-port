package dev.craftmine.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.color.block.BlockColors;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.block.MobTrophyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Ported from the fork's BlockColors.createDefault additions:
 *   blockColors.register((state, level, pos, tint) -> 0, Blocks.SKY);
 *   blockColors.register((state, level, pos, tint) -> state.getValueOrElse(
 *       MobTrophyBlock.GRADE, Grade.GRASS).color(), Blocks.MOB_TROPHY);
 * Without the sky registration the "SKY LOL" texture gets biome-tinted
 * (grass green) - reported as the hub walls looking wrong.
 * 26.3's register takes a List<BlockTintSource>; createDefault is static, so
 * the instance is captured via ModifyReturnValue (MixinExtras ships with the
 * loader - the game log shows MixinExtrasServiceImpl initializing).
 */
@Mixin(BlockColors.class)
public abstract class BlockColorsMixin {

    @ModifyReturnValue(method = "createDefault", at = @At("TAIL"))
    private static BlockColors craftmine$registerBlockTints(BlockColors blockColors) {
        // Fork registers -> 0 for SKY, and that is load-bearing: the fork's
        // sky_block shader uses the vertex tint as the live-sky mix weight
        // (fsh: mix(skyColor, textureColor, glintColor * texAlpha) where
        // glintColor = glint * tint). Tint black => glint term is zero =>
        // sky blocks are a pure screen-space sample of the live sky: NO
        // glint streaks and NO trace of the original texture - exactly the
        // reference video ("the blocks don't have a texture ... they have no
        // glint ... the dimension control block is the one that does", which
        // keeps its default white tint + animated glint).
        blockColors.register(List.of(blockState -> 0), CraftmineContent.SKY);
        blockColors.register(
            List.of(blockState -> blockState.getValueOrElse(MobTrophyBlock.GRADE, MobTrophyBlock.Grade.GRASS).color()),
            CraftmineContent.MOB_TROPHY);
        return blockColors;
    }
}
