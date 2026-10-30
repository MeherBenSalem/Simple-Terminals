package com.nightbeam.omnivault.storage;

import java.util.function.ObjLongConsumer;

/** Counted fluid storage in millibuckets: the fluid counterpart of {@link ItemStorage}. */
public interface FluidStorage {
    long insert(FluidKey key, long amount, boolean simulate);

    long extract(FluidKey key, long amount, boolean simulate);

    void forEach(ObjLongConsumer<FluidKey> consumer);

    default long getAmount(FluidKey key) {
        long[] total = {0};
        forEach((k, amount) -> {
            if (k.equals(key)) {
                total[0] += amount;
            }
        });
        return total[0];
    }

    default int priority() {
        return 0;
    }

    default boolean isPreferredFor(FluidKey key) {
        return getAmount(key) > 0;
    }

    /** As {@link ItemStorage#joinsTransactions()}. */
    default boolean joinsTransactions() {
        return false;
    }
}
