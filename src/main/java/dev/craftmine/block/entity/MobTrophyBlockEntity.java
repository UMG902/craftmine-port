package dev.craftmine.block.entity;

import org.jetbrains.annotations.Nullable;

import dev.craftmine.item.MobTrophyInfo;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Carries the trophy block's mob identity (as a data component so it also
 * survives on the picked-up item). The client renderer reads it via the
 * standard BE data sync to draw the right mob head.
 */
public class MobTrophyBlockEntity extends BlockEntity {

    public MobTrophyBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(CraftmineContent.MOB_TROPHY_BLOCK_ENTITY, blockPos, blockState);
    }

    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return this.saveWithoutMetadata(provider);
    }

    @Nullable
    public MobTrophyInfo getEntityType() {
        return this.components().get(CraftmineDataComponents.MOB_TROPHY_TYPE);
    }
}
