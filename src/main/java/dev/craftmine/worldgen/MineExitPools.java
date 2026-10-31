package dev.craftmine.worldgen;

import java.util.List;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Template pools for the mine exit structure: a root pool the jigsaw starts
 * from and a starts pool holding the exit room. The actual exit NBTs live
 * under data/minecraft/structure/mine_exits/.
 */
public class MineExitPools {

    public static final ResourceKey<StructureTemplatePool> ROOT = Pools.createKey("mine_exits/root");
    public static final ResourceKey<StructureTemplatePool> STARTS = Pools.createKey("mine_exits/starts");

    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureTemplatePool> pools = context.lookup(Registries.TEMPLATE_POOL);
        Holder.Reference<StructureTemplatePool> empty = pools.getOrThrow(Pools.EMPTY);
        context.register(ROOT, new StructureTemplatePool(empty,
            List.of(Pair.of(StructurePoolElement.single("mine_exits/root"), 1)),
            StructureTemplatePool.Projection.RIGID));
        context.register(STARTS, new StructureTemplatePool(empty,
            List.of(Pair.of(StructurePoolElement.single("mine_exits/exit_01"), 1)),
            StructureTemplatePool.Projection.RIGID));
    }
}
