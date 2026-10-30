package com.nightbeam.omnivault.platform.services;

import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public interface IPlatformHelper {
    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    Path getConfigDir();

    <T extends AbstractContainerMenu> MenuType<T> createMenuType(MenuFactory<T> factory);

    <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... blocks);

    CreativeModeTab.Builder creativeTabBuilder();

    /** Opens a menu whose client-side constructor reads {@code extraData}. */
    void openMenu(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraData);

    /** Item access for a block another mod (or vanilla) owns, e.g. a chest, drawer or machine. */
    Optional<SlotAccess> findSlotAccess(Level level, BlockPos pos, @Nullable Direction side);

    /** Fluid tanks of a block another mod (or vanilla, e.g. a cauldron) owns. */
    Optional<FluidAccess> findFluidAccess(Level level, BlockPos pos, @Nullable Direction side);

    /** Sends a payload registered in {@code ModPackets} to the server. Client only. */
    void sendToServer(CustomPacketPayload payload);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    @FunctionalInterface
    interface MenuFactory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory inventory, RegistryFriendlyByteBuf data);
    }

    @FunctionalInterface
    interface BlockEntityFactory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }
}
