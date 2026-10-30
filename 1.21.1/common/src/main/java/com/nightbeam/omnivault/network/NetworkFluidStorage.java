package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.storage.FluidStorage;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

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
        long remaining = amount;
        for (FluidStorage storage : storages) {
            if (remaining > 0 && storage.isPreferredFor(key)) {
                remaining -= storage.insert(key, remaining, simulate);
            }
        }
        for (FluidStorage storage : storages) {
            if (remaining > 0) {
                remaining -= storage.insert(key, remaining, simulate);
            }
        }
        if (!simulate && remaining != amount) {
            changeCounter++;
        }
        return amount - remaining;
    }

    @Override
    public long extract(FluidKey key, long amount, boolean simulate) {
        long remaining = amount;
        for (FluidStorage storage : extractOrder) {
            if (remaining > 0) {
                remaining -= storage.extract(key, remaining, simulate);
            }
        }
        if (!simulate && remaining != amount) {
            changeCounter++;
        }
        return amount - remaining;
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
