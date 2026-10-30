package com.nightbeam.omnivault.storage;

import java.util.function.ObjLongConsumer;

/**
 * Counted item storage: what the network sees. Amounts are longs so cells and drawers can hold far
 * more than a stack.
 */
public interface ItemStorage {
    /** @return how many were (or would be) accepted. */
    long insert(ItemKey key, long amount, boolean simulate);

    /** @return how many were (or would be) removed. */
    long extract(ItemKey key, long amount, boolean simulate);

    /** Reports every stored type once with its total count. */
    void forEach(ObjLongConsumer<ItemKey> consumer);

    default long getCount(ItemKey key) {
        long[] total = {0};
        forEach((k, count) -> {
            if (k.equals(key)) {
                total[0] += count;
            }
        });
        return total[0];
    }

    /** Higher priorities are filled first and emptied last. */
    default int priority() {
        return 0;
    }

    /** Storages that can hold this key only when it is already present (drawers, partitioned cells) return true. */
    default boolean isPreferredFor(ItemKey key) {
        return getCount(key) > 0;
    }

    /** Monotonic counter that changes whenever contents change, or -1 if contents must be polled. */
    default long version() {
        return -1;
    }
}
