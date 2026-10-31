package dev.craftmine.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.3 removed the fork's unfiltered 3-arg StructureTemplate.filterBlocks(pos,
 * settings, absolute) overload (fork ezw.a(ix,ezs,boolean), which iterated
 * Palette.blocks() - ALL blocks). The surviving 4-arg overload routes through
 * Palette.blocks(Block), and Palette.blocks(null) filters EVERYTHING out
 * (BlockState.is(null) is a reference comparison that is always false), so
 * passing null silently yields an empty list instead of "no filter" like the
 * fork did. DoorMenu.extractLayout needs the fork semantics, so we expose the
 * palette list and reimplement the removed overload 1:1.
 */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
    @Accessor("palettes")
    List<StructureTemplate.Palette> craftmine$palettes();
}
