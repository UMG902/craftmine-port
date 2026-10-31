package dev.craftmine.client.render;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import dev.craftmine.block.MobTrophyBlock;

public record GradeColor() implements ItemTintSource
{
    public static final MapCodec<GradeColor> MAP_CODEC = MapCodec.unit(new GradeColor());

    @Override
    public int calculate(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity) {
        MobTrophyBlock.Grade grade;
        BlockItemStateProperties blockItemStateProperties = itemStack.get(DataComponents.BLOCK_STATE);
        if (blockItemStateProperties != null && (grade = blockItemStateProperties.get(MobTrophyBlock.GRADE)) != null) {
            return ARGB.opaque(grade.color());
        }
        return ARGB.opaque(MobTrophyBlock.Grade.GRASS.color());
    }

    public MapCodec<GradeColor> type() {
        return MAP_CODEC;
    }
}

