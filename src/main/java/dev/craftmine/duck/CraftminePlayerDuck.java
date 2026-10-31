package dev.craftmine.duck;

import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import dev.craftmine.unlock.ServerPlayerUnlocks;
import dev.craftmine.server.TheGame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import dev.craftmine.item.component.RoomerinoComponentino;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import dev.craftmine.unlock.PlayerUnlock;

/**
 * Duck interface for craftmine additions to ServerPlayer.
 */
public interface CraftminePlayerDuck {
    TheGame craftmine$theGame();

    void addHubReward(ItemStack itemStack);

    void swapInventoryToHub();

    void swapInventoryFromHub();

    void makeVisible(Holder<PlayerUnlock> holder);

    boolean isUnlocked(Holder<PlayerUnlock> holder);

    boolean isActive(Holder<PlayerUnlock> holder);

    boolean buyUnlock(Holder<PlayerUnlock> holder);

    void reactivateUnlock(Holder<PlayerUnlock> holder);

    /** Unoriginal feature: relock an unlock (hub only, see CraftmineRelock). Returns true when it was relocked. */
    boolean relockUnlock(Holder<PlayerUnlock> holder);

    void forceUnlock(Holder<PlayerUnlock> holder);

    boolean donateExperienceToMineCrafter();

    void onMineEntered();

    boolean isRevisiting();

    void setRevisiting(boolean revisiting);

    void openDoor(ItemStack keyItem, BlockPos blockPos, Direction direction, DoubleBlockHalf half);

    @Nullable
    ItemEntity craftmine$drop(ItemStack itemStack, boolean throwRandomly, boolean preserveItem, boolean includeThrowerName);

    ServerPlayerUnlocks craftmine$playerUnlocks();

    void addOrDropItem(ItemStack itemStack);

    int getTotalExperienceBasedOnLevels();

    void craftmine$tickUnlocks();
}
