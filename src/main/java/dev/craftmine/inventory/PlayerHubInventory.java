package dev.craftmine.inventory;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The hub player's extra inventory rows: the items that are parked here while
 * the player is inside a mine and handed back when they return to the hub.
 *
 * <p>Persistence: the container itself is written to and read from the
 * <b>player data</b> (playerdata/&lt;uuid&gt;.dat) by {@code ServerPlayerMixin}
 * via {@link #save(ValueOutput, String)} / {@link #load(ValueInput, String)}.
 * This is what keeps the stash alive across the config-phase client resync on
 * mine entry (which replaces the ServerPlayer instance) and across relogs while
 * inside a mine. The tag shape is one compound per stack with a "Slot" byte,
 * same as vanilla inventory slots.
 */
public class PlayerHubInventory extends SimpleContainer {

    private static final String SLOT_KEY = "Slot";
    private static final String ITEM_KEY = "Item";

    public PlayerHubInventory(int size) {
        super(size);
    }

    public void fromTag(ListTag listTag, HolderLookup.Provider provider) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            this.setItem(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag compoundTag = listTag.getCompoundOrEmpty(i);
            int slot = compoundTag.getByteOr(SLOT_KEY, (byte)0) & 0xFF;
            if (slot >= 0 && slot < this.getContainerSize()) {
                ItemStack itemStack = ItemStack.OPTIONAL_CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), compoundTag).result().orElse(ItemStack.EMPTY);
                this.setItem(slot, itemStack);
            }
        }
    }

    public ListTag createTag(HolderLookup.Provider provider) {
        ListTag listTag = new ListTag();
        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack itemStack = this.getItem(i);
            if (itemStack.isEmpty()) {
                continue;
            }
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putByte(SLOT_KEY, (byte)i);
            compoundTag.merge((CompoundTag)ItemStack.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), itemStack).getOrThrow());
            listTag.add(compoundTag);
        }
        return listTag;
    }

    /**
     * Writes the non-empty stacks (with their slot indices) into a named list
     * of the player's save data. Empty leading/trailing slots are simply
     * skipped; the slot byte preserves the exact positions.
     */
    public void save(ValueOutput output, String key) {
        ValueOutput.ValueOutputList list = output.childrenList(key);
        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack itemStack = this.getItem(i);
            if (itemStack.isEmpty()) {
                continue;
            }
            ValueOutput slot = list.addChild();
            slot.putByte(SLOT_KEY, (byte)i);
            slot.store(ITEM_KEY, ItemStack.CODEC, itemStack);
        }
    }

    /**
     * Clears the container and restores the stacks written by {@link #save}.
     * Entries with out-of-range slot indices are dropped instead of throwing,
     * so a shrunk inventory size degrades gracefully.
     */
    public void load(ValueInput input, String key) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            this.setItem(i, ItemStack.EMPTY);
        }
        for (ValueInput slot : input.childrenListOrEmpty(key)) {
            int index = slot.getByteOr(SLOT_KEY, (byte)0) & 0xFF;
            if (index >= 0 && index < this.getContainerSize()) {
                slot.read(ITEM_KEY, (Codec<ItemStack>)ItemStack.OPTIONAL_CODEC).ifPresent(itemStack -> this.setItem(index, itemStack));
            }
        }
    }
}
