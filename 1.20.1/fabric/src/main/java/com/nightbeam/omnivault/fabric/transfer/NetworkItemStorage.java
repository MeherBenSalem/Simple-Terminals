package com.nightbeam.omnivault.fabric.transfer;

import com.nightbeam.omnivault.storage.ChangeJournal;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A window onto a storage network (the Network Port, a Pattern Provider's return slot) as Fabric item
 * storage, with one view per stored item at its full count. Changes apply at once inside the caller's
 * transaction. Part of a change can land in other mods' storage behind a Storage Bus, which rolls back
 * by itself; the network records how to undo the rest (see {@link ChangeJournal}) and only hands items
 * to waiting crafting jobs once the outermost transaction commits.
 */
public final class NetworkItemStorage extends SnapshotParticipant<Long> implements Storage<ItemVariant> {
    private final SlotAccess access;
    private final ItemStorage network;
    private final ChangeJournal journal = new ChangeJournal();

    public NetworkItemStorage(SlotAccess access, ItemStorage network) {
        this.access = access;
        this.network = network;
    }

    public SlotAccess access() {
        return access;
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (maxAmount == 0) {
            return 0;
        }
        ItemKey key = SlotAccessItemStorage.key(resource);
        updateSnapshots(transaction);
        return journal.record(() -> network.insert(key, maxAmount, false));
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (maxAmount == 0) {
            return 0;
        }
        ItemKey key = SlotAccessItemStorage.key(resource);
        updateSnapshots(transaction);
        return journal.record(() -> network.extract(key, maxAmount, false));
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        List<StorageView<ItemVariant>> views = new ArrayList<>();
        network.forEach((key, count) -> views.add(new KeyView(key)));
        return views.iterator();
    }

    @Override
    protected Long createSnapshot() {
        return journal.mark();
    }

    @Override
    protected void readSnapshot(Long mark) {
        journal.rollBackTo(mark);
    }

    @Override
    protected void onFinalCommit() {
        journal.commit();
    }

    private final class KeyView implements StorageView<ItemVariant> {
        private final ItemKey key;

        KeyView(ItemKey key) {
            this.key = key;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return key.matches(resource.toStack()) ? NetworkItemStorage.this.extract(resource, maxAmount, transaction) : 0;
        }

        @Override
        public boolean isResourceBlank() {
            return false;
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(key.view());
        }

        @Override
        public long getAmount() {
            return network.getCount(key);
        }

        @Override
        public long getCapacity() {
            return getAmount();
        }
    }
}
