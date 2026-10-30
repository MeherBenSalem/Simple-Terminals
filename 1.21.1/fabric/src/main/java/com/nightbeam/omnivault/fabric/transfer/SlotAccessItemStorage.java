package com.nightbeam.omnivault.fabric.transfer;

import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.SlotAccess;
import com.nightbeam.omnivault.storage.SlotAccessStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * One of our slotted inventories (vaults, drawers, backpacks, the trash can) as a Fabric item storage.
 * Our inventories take a simulate flag rather than a transaction, so each change is applied at once
 * and the way to undo it is recorded; an aborted transaction runs the undo steps backwards. Windows
 * onto a network use {@link NetworkItemStorage} instead.
 */
public final class SlotAccessItemStorage extends SnapshotParticipant<Integer> implements SlottedStorage<ItemVariant> {
    private final SlotAccess access;
    private final SlotAccessStorage counted;
    private final Consumer<ItemStack> overflow;
    private final List<Runnable> undo = new ArrayList<>();

    /**
     * @param overflow where items go when an undo step finds less room than before (something else
     *                 changed the inventory meanwhile); normally dropped at the block
     */
    public SlotAccessItemStorage(SlotAccess access, Consumer<ItemStack> overflow) {
        this.access = access;
        this.counted = new SlotAccessStorage(access);
        this.overflow = overflow;
    }

    public SlotAccess access() {
        return access;
    }

    static ItemKey key(ItemVariant variant) {
        return ItemKey.of(variant.toStack());
    }

    // -- the inventory as a whole --------------------------------------------------------------------

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        return insert(-1, key(resource), maxAmount, transaction);
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        return extract(-1, key(resource), maxAmount, transaction);
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        List<StorageView<ItemVariant>> views = new ArrayList<>(getSlotCount());
        for (int slot = 0; slot < getSlotCount(); slot++) {
            views.add(getSlot(slot));
        }
        return views.iterator();
    }

    @Override
    public int getSlotCount() {
        return access.getSlots();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        return new Slot(slot);
    }

    // -- operations ------------------------------------------------------------------------------------

    private long insert(int slot, ItemKey key, long maxAmount, TransactionContext transaction) {
        if (maxAmount == 0) {
            return 0;
        }
        updateSnapshots(transaction);
        long before = held(slot, key);
        long inserted = doInsert(slot, key, maxAmount);
        // Undo what was actually stored: a void upgrade can accept more than it keeps.
        long stored = held(slot, key) - before;
        if (stored > 0) {
            undo.add(() -> {
                long taken = doExtract(slot, key, stored);
                if (slot >= 0 && taken < stored) {
                    doExtract(-1, key, stored - taken);
                }
            });
        }
        return inserted;
    }

    private long extract(int slot, ItemKey key, long maxAmount, TransactionContext transaction) {
        if (maxAmount == 0) {
            return 0;
        }
        updateSnapshots(transaction);
        long extracted = doExtract(slot, key, maxAmount);
        if (extracted > 0) {
            undo.add(() -> putBack(slot, key, extracted));
        }
        return extracted;
    }

    private long doInsert(int slot, ItemKey key, long amount) {
        if (slot < 0) {
            return counted.insert(key, amount, false);
        }
        long done = 0;
        while (done < amount) {
            int chunk = (int) Math.min(amount - done, Math.max(1, key.maxStackSize()));
            ItemStack rest = access.insertItem(slot, key.toStack(chunk), false);
            int took = chunk - rest.getCount();
            if (took <= 0) {
                break;
            }
            done += took;
        }
        return done;
    }

    /** Real extraction; handlers often give out one stack per call, so keep asking. */
    private long doExtract(int slot, ItemKey key, long amount) {
        if (slot < 0) {
            return counted.extract(key, amount, false);
        }
        long done = 0;
        while (done < amount && key.matches(access.getStackInSlot(slot))) {
            ItemStack taken = access.extractItem(slot, (int) Math.min(amount - done, Integer.MAX_VALUE), false);
            if (!key.matches(taken)) {
                break;
            }
            done += taken.getCount();
        }
        return done;
    }

    private void putBack(int slot, ItemKey key, long amount) {
        long left = amount;
        if (slot >= 0) {
            left -= doInsert(slot, key, left);
        }
        if (left > 0) {
            left -= counted.insert(key, left, false);
        }
        spill(key, left);
    }

    private void spill(ItemKey key, long amount) {
        long left = amount;
        while (left > 0) {
            int count = (int) Math.min(left, key.maxStackSize());
            overflow.accept(key.toStack(count));
            left -= count;
        }
    }

    private long held(int slot, ItemKey key) {
        return slot < 0 ? counted.getCount(key) : heldInSlot(slot, key);
    }

    private long heldInSlot(int slot, ItemKey key) {
        ItemStack stack = access.getStackInSlot(slot);
        return key.matches(stack) ? stack.getCount() : 0;
    }

    // -- transaction bookkeeping ---------------------------------------------------------------------

    @Override
    protected Integer createSnapshot() {
        return undo.size();
    }

    @Override
    protected void readSnapshot(Integer size) {
        while (undo.size() > size) {
            undo.remove(undo.size() - 1).run();
        }
    }

    @Override
    protected void onFinalCommit() {
        undo.clear();
    }

    // -- slots ---------------------------------------------------------------------------------------

    private final class Slot implements SingleSlotStorage<ItemVariant> {
        private final int slot;

        Slot(int slot) {
            this.slot = slot;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            return SlotAccessItemStorage.this.insert(slot, key(resource), maxAmount, transaction);
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            ItemKey key = key(resource);
            if (!key.matches(access.getStackInSlot(slot))) {
                return 0;
            }
            return SlotAccessItemStorage.this.extract(slot, key, maxAmount, transaction);
        }

        @Override
        public boolean isResourceBlank() {
            return access.getStackInSlot(slot).isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(access.getStackInSlot(slot));
        }

        @Override
        public long getAmount() {
            return access.getStackInSlot(slot).getCount();
        }

        @Override
        public long getCapacity() {
            return access.getSlotLimit(slot);
        }
    }
}
