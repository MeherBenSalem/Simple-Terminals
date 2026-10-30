package com.nightbeam.omnivault.network.api;

import com.nightbeam.omnivault.network.StorageNetwork;
import org.jetbrains.annotations.Nullable;

/** A network device's block entity. The core assigns the network when it (re)builds. */
public interface NetworkNode {
    @Nullable
    StorageNetwork getNetwork();

    void setNetwork(@Nullable StorageNetwork network);

    /** FE per tick while the network runs on power. */
    default int energyUsage() {
        return 1;
    }

    /** The network went online or offline (or this node joined/left one). */
    default void onNetworkStateChanged() {
    }
}
