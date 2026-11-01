package com.nightbeam.omnivault.neoforge.transfer;

import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Another mod's (or vanilla's) item handler seen as one of our slotted inventories. A simulated change
 * is a transaction that is never committed.
 */
public record ItemHandlerSlotAccess(ResourceHandler<ItemResource> handler) implements SlotAccess {
    @Override
    public boolean joinsTransactions() {
        return true;
    }

    @Override
    public int getSlots() {
        return handler.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= handler.size()) {
            return ItemStack.EMPTY;
        }
        ItemResource resource = handler.getResource(slot);
        return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(handler.getAmountAsInt(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= handler.size() || Transactions.closing()) {
            return stack;
        }
        int inserted;
        try (Transaction transaction = Transactions.open()) {
            inserted = handler.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
            if (!simulate) {
                transaction.commit();
            }
        }
        return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= handler.size() || Transactions.closing()) {
            return ItemStack.EMPTY;
        }
        ItemResource resource = handler.getResource(slot);
        if (resource.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int extracted;
        try (Transaction transaction = Transactions.open()) {
            extracted = handler.extract(slot, resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
        }
        return extracted <= 0 ? ItemStack.EMPTY : resource.toStack(extracted);
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot < 0 || slot >= handler.size() ? 0 : handler.getCapacityAsInt(slot, handler.getResource(slot));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return stack.isEmpty() || slot >= 0 && slot < handler.size() && handler.isValid(slot, ItemResource.of(stack));
    }
}
