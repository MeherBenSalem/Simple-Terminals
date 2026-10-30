package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.storage.ChangeJournal;
import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.storage.FluidStorage;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ObjLongConsumer;

/** Every fluid storage on a network seen as one, with the same priority rules as items. */
public class NetworkFluidStorage implements FluidStorage {
    private final List<FluidStorage> storages = new ArrayList<>();
    private final List<FluidStorage> extractOrder = new ArrayList<>();
    private long changeCounter;

    void setStorages(List<FluidStorage> newStorages) {
        storages.clear();
        storages.addAll(newStorages);
        storages.sort(Comparator.comparingInt(FluidStorage::priority).reversed());
        extractOrder.clear();
        extractOrder.addAll(storages);
        extractOrder.sort(Comparator.comparingInt(FluidStorage::priority));
        changeCounter++;
    }

    public boolean isEmpty() {
        return storages.isEmpty();
    }

    public long changeCounter() {
        return changeCounter;
    }

    void poll() {
        // External tanks can change without telling us; terminals compare snapshots anyway.
        changeCounter++;
    }

    @Override
    public long insert(FluidKey key, long amount, boolean simulate) {
        ChangeJournal journal = simulate ? null : ChangeJournal.current();
        boolean record = journal != null && journal.enter();
        try {
            long remaining = amount;
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                for (FluidStorage storage : storages) {
                    if (remaining > 0 && (pass == 1 || storage.isPreferredFor(key))) {
                        remaining -= insertInto(storage, key, remaining, simulate, record ? journal : null);
                    }
                }
            }
            if (!simulate && remaining != amount) {
                changeCounter++;
            }
            return amount - remaining;
        } finally {
            if (journal != null) {
                journal.exit();
            }
        }
    }

    @Override
    public long extract(FluidKey key, long amount, boolean simulate) {
        ChangeJournal journal = simulate ? null : ChangeJournal.current();
        boolean record = journal != null && journal.enter();
        try {
            long remaining = amount;
            for (FluidStorage storage : extractOrder) {
                if (remaining > 0) {
                    long got = storage.extract(key, remaining, simulate);
                    remaining -= got;
                    // Inside a loader transaction: note how to put our own storage back (see ChangeJournal).
                    if (got > 0 && record && !storage.joinsTransactions()) {
                        journal.addUndo(() -> {
                            storage.insert(key, got, false);
                            changeCounter++;
                        });
                    }
                }
            }
            if (!simulate && remaining != amount) {
                changeCounter++;
            }
            return amount - remaining;
        } finally {
            if (journal != null) {
                journal.exit();
            }
        }
    }

    private long insertInto(FluidStorage storage, FluidKey key, long amount, boolean simulate, @Nullable ChangeJournal journal) {
        if (journal == null || storage.joinsTransactions()) {
            return storage.insert(key, amount, simulate);
        }
        long before = storage.getAmount(key);
        long accepted = storage.insert(key, amount, false);
        long stored = storage.getAmount(key) - before;
        if (stored > 0) {
            journal.addUndo(() -> {
                storage.extract(key, stored, false);
                changeCounter++;
            });
        }
        return accepted;
    }

    @Override
    public void forEach(ObjLongConsumer<FluidKey> consumer) {
        Object2LongOpenHashMap<FluidKey> totals = new Object2LongOpenHashMap<>();
        for (FluidStorage storage : storages) {
            storage.forEach(totals::addTo);
        }
        totals.object2LongEntrySet().forEach(entry -> consumer.accept(entry.getKey(), entry.getLongValue()));
    }

    @Override
    public long getAmount(FluidKey key) {
        long total = 0;
        for (FluidStorage storage : storages) {
            total += storage.getAmount(key);
        }
        return total;
    }
}
