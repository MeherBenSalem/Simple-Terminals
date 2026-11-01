package com.nightbeam.omnivault.network.api;

import com.nightbeam.omnivault.network.StorageNetwork;

/** A node the network ticks while it is active (buses, emitters). */
public interface TickingNode {
    void networkTick(StorageNetwork network, long gameTime);
}
