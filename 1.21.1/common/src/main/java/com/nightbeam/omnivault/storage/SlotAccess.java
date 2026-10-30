package com.nightbeam.omnivault.storage;

import net.minecraft.world.item.ItemStack;

/**
 * A slotted inventory, mirroring the loader item-handler contract (insert returns the remainder,
 * extract returns what was removed). Loaders adapt their own handler types to and from this.
 */
public interface SlotAccess {
    int getSlots();

    /** Do not modify the returned stack. */
    ItemStack getStackInSlot(int slot);

    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    ItemStack extractItem(int slot, int amount, boolean simulate);

    int getSlotLimit(int slot);

    default boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    /** Replaces a slot outright; used by menus. Defaults to extract-then-insert. */
    default void setStackInSlot(int slot, ItemStack stack) {
        ItemStack current = getStackInSlot(slot);
        if (!current.isEmpty()) {
            extractItem(slot, current.getCount(), false);
        }
        if (!stack.isEmpty()) {
            insertItem(slot, stack, false);
        }
    }
}
