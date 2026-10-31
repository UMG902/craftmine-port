package dev.craftmine.level.biome;

import com.mojang.serialization.Codec;
import dev.craftmine.level.biome.CraftmineBiomeExitTypes;
import net.minecraft.util.StringRepresentable;

/**
 * Ported from the 25w14craftmine BiomeSpecialEffects.ExitType enum.
 * 26.3's BiomeSpecialEffects record has no exit-type field, so the value is
 * tracked per-biome in CraftmineBiomeExitTypes.
 */
public enum CraftmineExitType implements StringRepresentable {
    NONE("none"),
    SURFACE("surface"),
    RARE_SURFACE("rare_surface"),
    CAVE("cave");

    public static final Codec<CraftmineExitType> CODEC = StringRepresentable.fromEnum(CraftmineExitType::values);

    private final String id;

    private CraftmineExitType(String string) {
        this.id = string;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
