package com.nightbeam.omnivault.backpack;

import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.world.item.ItemStack;

/** A backpack's contents seen by hoppers, pipes and other mods (the placed backpack's item capability). */
public record BackpackSlotAccess(BackpackInventory inventory) implements SlotAccess {
    @Override
    public int getSlots() {
        return inventory.getContainerSize();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getItem(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= getSlots() || !inventory.accepts(stack)) {
            return stack;
        }
        ItemStack existing = inventory.getItem(slot);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) {
            return stack;
        }
        int limit = Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize());
        int moved = Math.min(limit - existing.getCount(), stack.getCount());
        if (moved <= 0) {
            return stack;
        }
        if (!simulate) {
            inventory.setItem(slot, stack.copyWithCount(existing.getCount() + moved));
        }
        return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= getSlots()) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = inventory.getItem(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(amount, Math.min(existing.getCount(), existing.getMaxStackSize()));
        ItemStack result = existing.copyWithCount(taken);
        if (!simulate) {
            inventory.setItem(slot, existing.getCount() == taken ? ItemStack.EMPTY : existing.copyWithCount(existing.getCount() - taken));
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return inventory.accepts(stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        inventory.setItem(slot, stack);
    }
}
