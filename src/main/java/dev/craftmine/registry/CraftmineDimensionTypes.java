package dev.craftmine.registry;

import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Ported craftmine additions to DimensionType: dark-cave ambience and
 * piglin-safe variants. 26.3's record is reconstructed with modified fields;
 * piglin safety is expressed via the PIGLINS_ZOMBIFY environment attribute.
 */
public final class CraftmineDimensionTypes {
    private CraftmineDimensionTypes() {
    }

    public static DimensionType withAmbientLight(DimensionType type) {
        return new DimensionType(type.hasFixedTime(), type.hasSkyLight(), type.hasCeiling(), type.hasEnderDragonFight(),
            type.coordinateScale(), type.minY(), type.height(), type.logicalHeight(), type.infiniburn(),
            0.1f, new DimensionType.MonsterSettings(ConstantInt.of(7), 15), type.skybox(),
            type.cardinalLightType(), type.attributes(), type.timelines(), type.defaultClock());
    }

    public static DimensionType asPiglinSafe(DimensionType type) {
        EnvironmentAttributeMap.Builder attributes = EnvironmentAttributeMap.builder().putAll(type.attributes());
        attributes.set(EnvironmentAttributes.PIGLINS_ZOMBIFY, false);
        return new DimensionType(type.hasFixedTime(), type.hasSkyLight(), type.hasCeiling(), type.hasEnderDragonFight(),
            type.coordinateScale(), type.minY(), type.height(), type.logicalHeight(), type.infiniburn(),
            type.ambientLight(), type.monsterSettings(), type.skybox(), type.cardinalLightType(),
            attributes.build(), type.timelines(), type.defaultClock());
    }
}
