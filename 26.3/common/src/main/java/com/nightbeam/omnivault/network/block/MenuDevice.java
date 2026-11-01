package com.nightbeam.omnivault.network.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Block entities that open a menu on right-click and may drop items when broken. */
public interface MenuDevice {
    void openMenu(ServerPlayer player);

    default void dropContents(Level level, BlockPos pos) {
    }
}
