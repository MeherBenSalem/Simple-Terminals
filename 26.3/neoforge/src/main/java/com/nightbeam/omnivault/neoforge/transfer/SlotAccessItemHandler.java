package com.nightbeam.omnivault.neoforge.transfer;

import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import com.nightbeam.omnivault.storage.SlotAccess;
import com.nightbeam.omnivault.storage.SlotAccessStorage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One of our slotted inventories (vaults, drawers, backpacks, the trash can) as a NeoForge item handler.
 * Our inventories take a simulate flag rather than a transaction, so each change is applied at once
 * and the way to undo it is recorded; an aborted transaction runs the undo steps backwards. Windows
 * onto a network use {@link NetworkItemHandler} instead.
 */
public final class SlotAccessItemHandler extends SnapshotJournal<Integer> implements ResourceHandler<ItemResource> {
    private final SlotAccess access;
    private final ItemStorage counted;
    private final Consumer<ItemStack> overflow;
    private final List<Runnable> undo = new ArrayList<>();

    /**
     * @param overflow where items go when an undo step finds less room than before (something else
     *                 changed the inventory meanwhile); normally dropped at the block
     */
    public SlotAccessItemHandler(SlotAccess access, Consumer<ItemStack> overflow) {
        this.access = access;
        // Drawers count their own contents in longs; anything else is counted slot by slot.
        this.counted = access instanceof ItemStorage storage ? storage : new SlotAccessStorage(access);
        this.overflow = overflow;
    }

    public SlotAccess access() {
        return access;
    }

    static ItemKey key(ItemResource resource) {
        return ItemKey.of(resource.toStack());
    }

    // -- slots ---------------------------------------------------------------------------------------

    @Override
    public int size() {
        return access.getSlots();
    }

    @Override
    public ItemResource getResource(int index) {
        return ItemResource.of(access.getStackInSlot(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return access.getStackInSlot(index).getCount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return resource.isEmpty() || isValid(index, resource) ? access.getSlotLimit(index) : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return access.isItemValid(index, resource.toStack());
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return (int) insert(index, key(resource), amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        ItemKey key = key(resource);
        if (!key.matches(access.getStackInSlot(index))) {
            return 0;
        }
        return (int) extract(index, key, amount, transaction);
    }

    // -- the inventory as a whole --------------------------------------------------------------------

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return (int) insert(-1, key(resource), amount, transaction);
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return (int) extract(-1, key(resource), amount, transaction);
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
    protected void revertToSnapshot(Integer size) {
        while (undo.size() > size) {
            undo.remove(undo.size() - 1).run();
        }
    }

    @Override
    protected void onRootCommit(Integer originalState) {
        undo.clear();
    }
}
