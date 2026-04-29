package com.nightbeam.simpleterminals.platform;

import com.nightbeam.simpleterminals.SimpleTerminals;
import com.nightbeam.simpleterminals.menu.CraftingTerminalMenu;
import com.nightbeam.simpleterminals.platform.services.IPlatformHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

import java.util.Optional;

public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Optional<Container> findPlatformContainer(Level level, BlockPos targetPos, Direction accessSide) {
        return Optional.empty();
    }

    @Override
    public void openCraftingTerminalMenu(ServerPlayer player, BlockPos targetPos, Direction accessSide, int slotCount, Component title) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
                buf.writeVarInt(slotCount);
            }

            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return CraftingTerminalMenu.createServer(containerId, inventory, targetPos, accessSide);
            }
        });
    }

    @Override
    public void sendCraftingTerminalScroll(int containerId, int offsetRows) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            FriendlyByteBuf buffer = PacketByteBufs.create();
            buffer.writeVarInt(containerId);
            buffer.writeVarInt(offsetRows);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(SimpleTerminals.CRAFTING_TERMINAL_SCROLL, buffer);
        }
    }

    @Override
    public void sendCraftingTerminalSearch(int containerId, String query) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            FriendlyByteBuf buffer = PacketByteBufs.create();
            buffer.writeVarInt(containerId);
            buffer.writeUtf(query, 50);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(SimpleTerminals.CRAFTING_TERMINAL_SEARCH, buffer);
        }
    }
}
