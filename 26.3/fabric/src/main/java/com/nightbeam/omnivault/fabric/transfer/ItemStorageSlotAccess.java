package com.nightbeam.omnivault.fabric.transfer;

import com.nightbeam.omnivault.storage.SlotAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Another mod's (or vanilla's) Fabric item storage seen as one of our slotted inventories. Slotted
 * storages (chests, most machines) keep their slots; anything else shows each of its views as a slot
 * plus one empty slot that inserts into the storage as a whole.
 */
public final class ItemStorageSlotAccess implements SlotAccess {
    private final Storage<ItemVariant> storage;

    public ItemStorageSlotAccess(Storage<ItemVariant> storage) {
        this.storage = storage;
    }

    /**
     * A transaction of our own: nested in one already open on this thread (another mod's transfer
     * reaching through a network), so it rolls back with it, or a new outer one.
     */
    static Transaction open() {
        return Transaction.getLifecycle() == Transaction.Lifecycle.OPEN
                ? Transaction.openNested(Transaction.getCurrentUnsafe())
                : Transaction.openOuter();
    }

    /** Fabric forbids transactions while one is closing; storage can't be changed then. */
    static boolean closing() {
        Transaction.Lifecycle lifecycle = Transaction.getLifecycle();
        return lifecycle == Transaction.Lifecycle.CLOSING || lifecycle == Transaction.Lifecycle.OUTER_CLOSING;
    }

    @Override
    public boolean joinsTransactions() {
        return true;
    }

    private List<StorageView<ItemVariant>> views() {
        List<StorageView<ItemVariant>> views = new ArrayList<>();
        for (StorageView<ItemVariant> view : storage) {
            views.add(view);
        }
        return views;
    }

    @Override
    public int getSlots() {
        if (storage instanceof SlottedStorage<ItemVariant> slotted) {
            return slotted.getSlotCount();
        }
        return storage.supportsInsertion() ? views().size() + 1 : views().size();
    }

    /** The view behind a slot, or null for the extra insert-only slot. */
    private StorageView<ItemVariant> view(int slot) {
        if (storage instanceof SlottedStorage<ItemVariant> slotted) {
            return slot >= 0 && slot < slotted.getSlotCount() ? slotted.getSlot(slot) : null;
        }
        List<StorageView<ItemVariant>> views = views();
        return slot >= 0 && slot < views.size() ? views.get(slot) : null;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        StorageView<ItemVariant> view = view(slot);
        if (view == null || view.isResourceBlank() || view.getAmount() <= 0) {
            return ItemStack.EMPTY;
        }
        return view.getResource().toStack((int) Math.min(view.getAmount(), Integer.MAX_VALUE));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || closing()) {
            return stack;
        }
        StorageView<ItemVariant> view = view(slot);
        long inserted;
        try (Transaction transaction = open()) {
            ItemVariant variant = ItemVariant.of(stack);
            if (storage instanceof SlottedStorage<ItemVariant> slotted) {
                inserted = view == null ? 0 : slotted.getSlot(slot).insert(variant, stack.getCount(), transaction);
            } else {
                // Unslotted storage decides where things go; any of its "slots" inserts into the whole.
                inserted = storage.insert(variant, stack.getCount(), transaction);
            }
            if (!simulate) {
                transaction.commit();
            }
        }
        return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount((int) (stack.getCount() - inserted));
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        StorageView<ItemVariant> view = view(slot);
        if (amount <= 0 || view == null || view.isResourceBlank() || closing()) {
            return ItemStack.EMPTY;
        }
        ItemVariant resource = view.getResource();
        long extracted;
        try (Transaction transaction = open()) {
            extracted = view.extract(resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
        }
        return extracted <= 0 ? ItemStack.EMPTY : resource.toStack((int) extracted);
    }

    @Override
    public int getSlotLimit(int slot) {
        StorageView<ItemVariant> view = view(slot);
        return view == null ? Integer.MAX_VALUE : (int) Math.min(view.getCapacity(), Integer.MAX_VALUE);
    }
}
