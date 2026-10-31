package dev.craftmine.block.entity;

import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.block.MineTravellingBlock;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.server.CraftmineServerHooks;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import dev.craftmine.inventory.MineCraftingMenu;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.phys.Vec3;
import dev.craftmine.registry.CraftmineContent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The mine crafter block entity: a 99-slot container (ingredient grid +
 * drawer rows read straight from it) plus the "travelled to mine" link.
 * Slot counts for the crafting menu scale with the shared mine-crafter
 * level ({@link #craftingSlotsForLevel}); when the linked travelling block's
 * mine completes, the mine item is flagged and rewards are dispensed.
 */
public class MineCrafterBlockEntity
extends BaseContainerBlockEntity
implements StackedContentsCompatible {
    public static final int MAX_WORLD_EFFECTS = 50;
    public static final int EXPERIENCE_DONATION_CHUNK_SIZE = 20;
    public static final int WIN_EXPERIENCE = 60;
    protected NonNullList<ItemStack> items = NonNullList.withSize(99, ItemStack.EMPTY);
    @Nullable
    private BlockPos mineTravellingBlockPos;
    private int dropRewardsInTicks = 0;

    public MineCrafterBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(CraftmineContent.MINE_CRAFTER_BLOCK_ENTITY, blockPos, blockState);
    }

    public static int experienceRequiredForLevel(int n) {
        if (n >= 15) {
            return 2000 + (n - 10) * 400;
        }
        if (n >= 5) {
            return 400 + (n - 5) * 160;
        }
        return 100 + Math.max(0, n) * 60;
    }

    public static int craftingSlotsForLevel(int n) {
        return Math.min(50, 3 + n);
    }

    public static int randomCraftingSlotsForLevel(int n) {
        return 1 + Mth.floor((float)MineCrafterBlockEntity.craftingSlotsForLevel(n) / 3.0f);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.mineTravellingBlockPos = input.read("mine_travelling_block_pos", BlockPos.CODEC).filter(Level::isInSpawnableBounds).orElse(null);
        this.dropRewardsInTicks = input.getIntOr("drop_rewards_in_ticks", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("mine_travelling_block_pos", BlockPos.CODEC, this.mineTravellingBlockPos);
        output.putInt("drop_rewards_in_ticks", this.dropRewardsInTicks);
        ContainerHelper.saveAllItems(output, this.items);
    }

    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return this.saveCustomOnly(provider);
    }

    /** Watches the placed travelling block: when its mine completes, flags the mine item and schedules reward dispensing. */
    public static void serverTick(Level level, BlockPos blockPos, BlockState blockState, MineCrafterBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean changed = false;
        if (blockEntity.dropRewardsInTicks > 0 && --blockEntity.dropRewardsInTicks == 0) {
            blockEntity.dispenseRewards(serverLevel, blockPos);
            blockEntity.setChanged();
        }
        if (blockEntity.mineTravellingBlockPos != null) {
            ItemStack mineItem = blockEntity.items.getFirst();
            if (mineItem == null) {
                blockEntity.mineTravellingBlockPos = null;
                changed = true;
            } else if (mineItem.get(CraftmineDataComponents.MINE_ACTIVE) != null && mineItem.get(CraftmineDataComponents.MINE_COMPLETED) == null
                && level.getBlockEntity(blockEntity.mineTravellingBlockPos) instanceof MineTravellingBlockEntity travellingBlock) {
                Level mineLevel = CraftmineServerHooks.theGame().getLevel(travellingBlock.getTargetDimension());
                if (mineLevel != null) {
                    CraftmineLevelDuck mineDuck = (CraftmineLevelDuck)mineLevel;
                    if (mineDuck.isMine() && mineDuck.isMineCompleted()) {
                        mineItem.set(CraftmineDataComponents.MINE_COMPLETED, mineDuck.isMineWon());
                        mineItem.remove(CraftmineDataComponents.MINE_ACTIVE);
                        level.destroyBlock(blockEntity.mineTravellingBlockPos, false, null);
                        blockEntity.mineTravellingBlockPos = null;
                        blockEntity.dropRewardsInTicks = 20;
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            MineCrafterBlockEntity.setChanged(level, blockPos, blockState);
        }
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> nonNullList) {
        this.items = nonNullList;
    }

    @Override
    public void setItem(int n, ItemStack itemStack) {
        this.items.set(n, itemStack);
        itemStack.limitSize(this.getMaxStackSize(itemStack));
        this.setChanged();
    }

    @Override
    public boolean canPlaceItem(int n, ItemStack itemStack) {
        return n != 0;
    }

    @Override
    public void fillStackedContents(StackedItemContents stackedItemContents) {
        for (ItemStack itemStack : this.items) {
            stackedItemContents.accountStack(itemStack);
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.mine_crafter");
    }

    @Override
    protected AbstractContainerMenu createMenu(int n, Inventory inventory) {
        return new MineCraftingMenu(n, inventory, ContainerLevelAccess.create(this.getLevel(), this.getBlockPos()), this, resourceKey -> this.makeMineTravellingBlock(this.getLevel(), this.getBlockPos(), resourceKey), this.getAdditionalData());
    }

    private void makeMineTravellingBlock(Level level, BlockPos blockPos, ResourceKey<LevelStem> resourceKey) {
        this.mineTravellingBlockPos = blockPos.above();
        MineTravellingBlock.createBlock(level, blockPos.above(), resourceKey, false);
        this.setChanged();
    }

    public static List<Integer> getAdditionalData(ServerLevel serverLevel) {
        int n = ((CraftmineLevelDuck)serverLevel).getMineCrafterLevel();
        int n2 = ((CraftmineLevelDuck)serverLevel).getMineCrafterExp();
        return List.of(n, n2);
    }

    public List<Integer> getAdditionalData() {
        Level level = this.getLevel();
        if (level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            return MineCrafterBlockEntity.getAdditionalData(serverLevel);
        }
        return List.of();
    }

    @Override
    public void preRemoveSideEffects(BlockPos blockPos, BlockState blockState) {
        Level level = this.getLevel();
        if (level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            int n = MineCrafterBlockEntity.randomCraftingSlotsForLevel(((CraftmineLevelDuck)serverLevel).getMineCrafterLevel());
            for (int i = 0; i < n + 1; ++i) {
                this.items.set(i, ItemStack.EMPTY);
            }
        }
        super.preRemoveSideEffects(blockPos, blockState);
    }

    /** Drops the finished mine item (with a hopper-style pop) and clears the crafter; plays win or lose depending on the outcome. */
    public void dispenseRewards(ServerLevel level, BlockPos blockPos) {
        ItemStack mineItem = this.getItem(0);
        Boolean completed = mineItem.get(CraftmineDataComponents.MINE_COMPLETED);
        if (completed == null) {
            return;
        }
        ItemEntity itemEntity = new ItemEntity(level, blockPos.getX(), blockPos.getY() + 2.5 + 1.0E-5, blockPos.getZ(), mineItem.copyWithCount(1));
        Vec3 popVelocity = new Vec3((level.random.nextDouble() * 0.2 - 0.1) * 2.0, level.random.nextDouble() * 0.4, (level.random.nextDouble() * 0.2 - 0.1) * 2.0);
        itemEntity.push(popVelocity);
        level.addFreshEntity(itemEntity);
        level.playSound(null, blockPos, completed ? SoundEvents.UI_LOOM_TAKE_RESULT : SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1.0f, 1.0f);
        ((CraftmineLevelDuck)level).dropRewards(blockPos);
        this.getItems().forEach(itemStack -> itemStack.setCount(0));
    }

}

