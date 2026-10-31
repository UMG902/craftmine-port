package dev.craftmine.worldgen;

import java.util.List;
import java.util.Optional;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import dev.craftmine.server.CraftmineServerHooks;

/**
 * Drops one of the given structure templates at the feature position —
 * the mine-start feature uses this to scatter its starter rooms. Rotation
 * is random unless the datapack forces one; the template is centered on
 * the placement position.
 *
 * <p>26.3 features are self-contained records implementing the Feature
 * interface; the old separate FeatureConfiguration/FeaturePlaceContext
 * pattern is gone.</p>
 */
public record PlaceTemplateFeature(List<Identifier> templates, Optional<Rotation> forcedRotation) implements Feature {

    public static final MapCodec<PlaceTemplateFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Identifier.CODEC.listOf().fieldOf("template").forGetter(PlaceTemplateFeature::templates),
        Rotation.CODEC.optionalFieldOf("forced_rotation").forGetter(PlaceTemplateFeature::forcedRotation)
    ).apply(instance, PlaceTemplateFeature::new));

    public PlaceTemplateFeature(List<Identifier> templates) {
        this(templates, Optional.empty());
    }

    public PlaceTemplateFeature(List<Identifier> templates, Rotation rotation) {
        this(templates, Optional.of(rotation));
    }

    @Override
    public MapCodec<PlaceTemplateFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        Rotation[] rotations = Rotation.values();
        Rotation rotation = this.forcedRotation().orElseGet(() -> rotations[random.nextInt(rotations.length)]);
        Identifier templateId = Util.getRandom(this.templates(), random);
        StructureTemplate template = CraftmineServerHooks.theGame().structureManager().getOrCreate(templateId);
        // One chunk of slack around the origin so big templates survive chunk borders.
        ChunkPos chunk = ChunkPos.containing(origin);
        BoundingBox bounds = new BoundingBox(
            chunk.getMinBlockX() - 16, level.getMinY(), chunk.getMinBlockZ() - 16,
            chunk.getMaxBlockX() + 16, level.getMaxY(), chunk.getMaxBlockZ() + 16);
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setRotation(rotation)
            .setBoundingBox(bounds)
            .setRandom(random)
            .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        Vec3i size = template.getSize(rotation);
        BlockPos corner = origin.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        BlockPos transformed = template.getZeroPositionWithTransform(corner, Mirror.NONE, rotation);
        template.placeInWorld(level, transformed, transformed, settings, random, 3);
        return true;
    }
}
