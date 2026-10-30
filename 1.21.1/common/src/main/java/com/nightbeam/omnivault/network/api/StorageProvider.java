package com.nightbeam.omnivault.network.api;

import com.nightbeam.omnivault.storage.ItemStorage;

import java.util.function.Consumer;

/** A node that contributes storage to the network (drives, storage buses). */
public interface StorageProvider {
    void collectStorages(Consumer<ItemStorage> out);

    /** Total item capacity it adds, for the network overview; -1 when unknown (external inventories). */
    default long capacity() {
        return -1;
    }
}
