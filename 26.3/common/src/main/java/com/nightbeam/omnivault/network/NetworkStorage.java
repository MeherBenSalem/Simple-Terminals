package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.storage.ChangeJournal;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * Every storage on a network seen as one. Inserts go to the highest priority first, preferring
 * storages that already hold the item; extracts drain the lowest priority first. Contents are
 * cached and kept up to date incrementally; storages that cannot report changes (chests behind a
 * storage bus) are rescanned once a second.
 */
public class NetworkStorage implements ItemStorage {
    private static final int RESCAN_INTERVAL = 20;

    private final List<ItemStorage> storages = new ArrayList<>();
    private final List<ItemStorage> extractOrder = new ArrayList<>();
    private final List<InsertInterceptor> interceptors = new ArrayList<>();
    private Object2LongOpenHashMap<ItemKey> cache = new Object2LongOpenHashMap<>();
    private boolean cacheValid;
    private boolean hasPolledStorage;
    private long knownVersionSum;
    private long changeCounter;
    private int ticks;

    void setStorages(List<ItemStorage> newStorages) {
        storages.clear();
        storages.addAll(newStorages);
        storages.sort(Comparator.comparingInt(ItemStorage::priority).reversed());
        extractOrder.clear();
        extractOrder.addAll(storages);
        extractOrder.sort(Comparator.comparingInt(ItemStorage::priority));
        hasPolledStorage = storages.stream().anyMatch(s -> s.version() < 0);
        invalidate();
    }

    public List<ItemStorage> storages() {
        return storages;
    }

    /** Sees every insert before any storage does (autocrafting collects the items its jobs wait for). */
    public void addInterceptor(InsertInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    public interface InsertInterceptor {
        /** @return how many of {@code amount} it takes (or would take, when simulating). */
        long intercept(ItemKey key, long amount, boolean simulate);
    }

    /** Increases whenever the visible contents change; terminals use it to skip work. */
    public long changeCounter() {
        return changeCounter;
    }

    void invalidate() {
        cacheValid = false;
        changeCounter++;
    }

    void tick() {
        if (++ticks % RESCAN_INTERVAL != 0) {
            return;
        }
        if (hasPolledStorage || versionSum() != knownVersionSum) {
            Object2LongOpenHashMap<ItemKey> previous = cache;
            boolean wasValid = cacheValid;
            rebuildCache();
            if (!wasValid || !previous.equals(cache)) {
                changeCounter++;
            }
        }
    }

    private long versionSum() {
        long sum = 0;
        for (ItemStorage storage : storages) {
            long version = storage.version();
            if (version > 0) {
                sum += version;
            }
        }
        return sum;
    }

    private void ensureCache() {
        if (!cacheValid) {
            rebuildCache();
        }
    }

    private void rebuildCache() {
        Object2LongOpenHashMap<ItemKey> fresh = new Object2LongOpenHashMap<>();
        for (ItemStorage storage : storages) {
            storage.forEach((key, count) -> fresh.addTo(key, count));
        }
        cache = fresh;
        cacheValid = true;
        knownVersionSum = versionSum();
    }

    @Override
    public long insert(ItemKey key, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        ChangeJournal journal = simulate ? null : ChangeJournal.current();
        if (journal != null) {
            boolean outermost = journal.enter();
            try {
                return insertJournaled(key, amount, journal, outermost);
            } finally {
                journal.exit();
            }
        }
        long remaining = amount;
        for (int i = 0; i < interceptors.size() && remaining > 0; i++) {
            remaining -= interceptors.get(i).intercept(key, remaining, simulate);
        }
        long intercepted = amount - remaining;
        for (ItemStorage storage : storages) {
            if (remaining <= 0) {
                break;
            }
            if (storage.isPreferredFor(key)) {
                remaining -= storage.insert(key, remaining, simulate);
            }
        }
        for (ItemStorage storage : storages) {
            if (remaining <= 0) {
                break;
            }
            remaining -= storage.insert(key, remaining, simulate);
        }
        long inserted = amount - remaining;
        if (!simulate && inserted > intercepted) {
            changed(key, inserted - intercepted);
        }
        return inserted;
    }

    @Override
    public long extract(ItemKey key, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        ChangeJournal journal = simulate ? null : ChangeJournal.current();
        if (journal != null) {
            boolean outermost = journal.enter();
            try {
                return extractJournaled(key, amount, journal, outermost);
            } finally {
                journal.exit();
            }
        }
        long remaining = amount;
        for (ItemStorage storage : extractOrder) {
            if (remaining <= 0) {
                break;
            }
            remaining -= storage.extract(key, remaining, simulate);
        }
        long extracted = amount - remaining;
        if (!simulate && extracted > 0) {
            changed(key, -extracted);
        }
        return extracted;
    }

    // -- inside a loader transaction ------------------------------------------------------------------

    /**
     * An insert that can be taken back: crafting jobs get nothing until the transaction commits (the
     * items wait in storage), and each change to our own storage records its undo step.
     */
    private long insertJournaled(ItemKey key, long amount, ChangeJournal journal, boolean record) {
        long remaining = amount;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (ItemStorage storage : storages) {
                if (remaining <= 0) {
                    break;
                }
                if (pass == 1 || storage.isPreferredFor(key)) {
                    remaining -= insertInto(storage, key, remaining, journal, record);
                }
            }
        }
        long inserted = amount - remaining;
        if (inserted > 0) {
            changed(key, inserted);
            if (!interceptors.isEmpty()) {
                journal.addOnCommit(() -> offerToJobs(key, inserted));
            }
        }
        return inserted;
    }

    private long insertInto(ItemStorage storage, ItemKey key, long amount, ChangeJournal journal, boolean record) {
        if (!record || storage.joinsTransactions()) {
            return storage.insert(key, amount, false);
        }
        long before = storage.getCount(key);
        long accepted = storage.insert(key, amount, false);
        // Undo what was kept: a void upgrade accepts more than it stores.
        long stored = storage.getCount(key) - before;
        if (stored > 0) {
            journal.addUndo(() -> changed(key, -storage.extract(key, stored, false)));
        }
        return accepted;
    }

    private long extractJournaled(ItemKey key, long amount, ChangeJournal journal, boolean record) {
        long remaining = amount;
        for (ItemStorage storage : extractOrder) {
            if (remaining <= 0) {
                break;
            }
            long got = storage.extract(key, remaining, false);
            remaining -= got;
            if (got > 0 && record && !storage.joinsTransactions()) {
                journal.addUndo(() -> {
                    long back = storage.insert(key, got, false);
                    changed(key, back);
                    if (back < got) {
                        putBackOwn(key, got - back);
                    }
                });
            }
        }
        long extracted = amount - remaining;
        if (extracted > 0) {
            changed(key, -extracted);
        }
        return extracted;
    }

    /**
     * After a transaction commits: crafting jobs waiting for this item claim it from our own storage.
     * Storage that joins transactions can't be touched now, while the loader is closing one.
     */
    private void offerToJobs(ItemKey key, long amount) {
        long wanted = 0;
        for (int i = 0; i < interceptors.size() && wanted < amount; i++) {
            wanted += interceptors.get(i).intercept(key, amount - wanted, true);
        }
        long taken = 0;
        for (ItemStorage storage : extractOrder) {
            if (taken >= wanted) {
                break;
            }
            if (!storage.joinsTransactions()) {
                taken += storage.extract(key, wanted - taken, false);
            }
        }
        if (taken <= 0) {
            return;
        }
        changed(key, -taken);
        long given = 0;
        for (int i = 0; i < interceptors.size() && given < taken; i++) {
            given += interceptors.get(i).intercept(key, taken - given, false);
        }
        if (given < taken) {
            putBackOwn(key, taken - given);
        }
    }

    /** Returns items to our own storage while a transaction is closing. */
    private void putBackOwn(ItemKey key, long amount) {
        long left = amount;
        for (ItemStorage storage : storages) {
            if (left <= 0) {
                break;
            }
            if (!storage.joinsTransactions()) {
                long back = storage.insert(key, left, false);
                changed(key, back);
                left -= back;
            }
        }
        if (left > 0) {
            OmniVault.LOG.warn("No room to put back {} x{} while undoing a transfer", key, left);
        }
    }

    private void changed(ItemKey key, long delta) {
        if (cacheValid) {
            long now = cache.addTo(key, delta) + delta;
            if (now <= 0) {
                cache.removeLong(key);
            }
            knownVersionSum = versionSum();
        }
        changeCounter++;
    }

    @Override
    public void forEach(ObjLongConsumer<ItemKey> consumer) {
        ensureCache();
        for (Object2LongMap.Entry<ItemKey> entry : cache.object2LongEntrySet()) {
            consumer.accept(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public long getCount(ItemKey key) {
        ensureCache();
        return cache.getLong(key);
    }

    public int typeCount() {
        ensureCache();
        return cache.size();
    }

    public long totalCount() {
        ensureCache();
        long total = 0;
        for (long value : cache.values()) {
            total += value;
        }
        return total;
    }
}
