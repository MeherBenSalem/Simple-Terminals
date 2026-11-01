package com.nightbeam.omnivault.neoforge.transfer;

import com.nightbeam.omnivault.storage.ChangeJournal;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A window onto a storage network (the Network Port, a Pattern Provider's return slot) as a NeoForge item
 * handler, with one index per stored item at its full count and a last, empty index that takes new
 * items. Changes apply at once inside the caller's transaction. Part of a change can land in other mods'
 * storage behind a Storage Bus, which rolls back by itself; the network records how to undo the rest (see
 * {@link ChangeJournal}) and only hands items to waiting crafting jobs once the root transaction commits.
 */
public final class NetworkItemHandler extends SnapshotJournal<Long> implements ResourceHandler<ItemResource> {
    private final SlotAccess access;
    private final ItemStorage network;
    private final ChangeJournal journal = new ChangeJournal();
    /** The stored items as of the last {@link #size()} call, which callers make before walking the indices. */
    private List<ItemKey> keys = List.of();

    public NetworkItemHandler(SlotAccess access, ItemStorage network) {
        this.access = access;
        this.network = network;
    }

    public SlotAccess access() {
        return access;
    }

    @Override
    public int size() {
        List<ItemKey> stored = new ArrayList<>();
        network.forEach((key, count) -> stored.add(key));
        keys = stored;
        return keys.size() + 1;
    }

    private ItemKey keyAt(int index) {
        return index >= 0 && index < keys.size() ? keys.get(index) : null;
    }

    @Override
    public ItemResource getResource(int index) {
        ItemKey key = keyAt(index);
        return key == null ? ItemResource.EMPTY : ItemResource.of(key.view());
    }

    @Override
    public long getAmountAsLong(int index) {
        ItemKey key = keyAt(index);
        return key == null ? 0 : network.getCount(key);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        ItemKey key = keyAt(index);
        return key == null ? Integer.MAX_VALUE : network.getCount(key);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return true;
    }

    /** The network decides where items go, whatever the index. */
    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return insert(resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        ItemKey key = keyAt(index);
        return key != null && key.matches(resource.toStack()) ? extract(resource, amount, transaction) : 0;
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0) {
            return 0;
        }
        ItemKey key = SlotAccessItemHandler.key(resource);
        updateSnapshots(transaction);
        return Transactions.clamp(journal.record(() -> network.insert(key, amount, false)));
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0) {
            return 0;
        }
        ItemKey key = SlotAccessItemHandler.key(resource);
        updateSnapshots(transaction);
        return Transactions.clamp(journal.record(() -> network.extract(key, amount, false)));
    }

    @Override
    protected Long createSnapshot() {
        return journal.mark();
    }

    @Override
    protected void revertToSnapshot(Long mark) {
        journal.rollBackTo(mark);
    }

    @Override
    protected void onRootCommit(Long originalState) {
        journal.commit();
    }
}
