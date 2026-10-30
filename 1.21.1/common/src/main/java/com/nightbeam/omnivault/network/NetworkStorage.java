package com.nightbeam.omnivault.network;

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
        long remaining = amount;
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
        if (!simulate && inserted > 0) {
            changed(key, inserted);
        }
        return inserted;
    }

    @Override
    public long extract(ItemKey key, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
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
