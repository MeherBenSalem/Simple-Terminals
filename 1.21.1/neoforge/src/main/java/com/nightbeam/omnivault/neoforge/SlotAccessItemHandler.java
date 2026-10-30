package com.nightbeam.omnivault.neoforge;

import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** One of our inventories exposed as a NeoForge item handler capability. */
public record SlotAccessItemHandler(SlotAccess access) implements IItemHandlerModifiable {
    @Override
    public int getSlots() {
        return access.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return access.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return access.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return access.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return access.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return access.isItemValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        access.setStackInSlot(slot, stack);
    }
}
