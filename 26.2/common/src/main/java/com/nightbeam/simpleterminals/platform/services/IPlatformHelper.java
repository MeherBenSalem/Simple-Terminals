package com.nightbeam.simpleterminals.platform.services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

import java.util.Optional;

public interface IPlatformHelper {
    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    Optional<Container> findPlatformContainer(Level level, BlockPos targetPos, Direction accessSide);

    void openCraftingTerminalMenu(ServerPlayer player, BlockPos targetPos, Direction accessSide, int slotCount, Component title);

    void sendCraftingTerminalScroll(int containerId, int offsetRows);

    void sendCraftingTerminalSearch(int containerId, String query);

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }
}
