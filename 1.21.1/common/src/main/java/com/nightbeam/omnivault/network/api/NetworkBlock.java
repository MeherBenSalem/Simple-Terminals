package com.nightbeam.omnivault.network.api;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Implemented by every block that carries the storage network: cables, the core and all devices. */
public interface NetworkBlock {
    /** Whether the network may pass through this block's {@code side}. */
    boolean canConnectNetwork(BlockState state, Direction side);

    /** Cable colour; blocks with different non-neutral colours never connect. -1 is neutral. */
    default int networkColor(BlockState state) {
        return -1;
    }

    static boolean colorsMatch(int a, int b) {
        return a < 0 || b < 0 || a == b;
    }
}
