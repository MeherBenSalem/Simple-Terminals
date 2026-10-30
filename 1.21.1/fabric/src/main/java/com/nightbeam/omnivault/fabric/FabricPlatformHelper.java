package com.nightbeam.omnivault.fabric;

import com.nightbeam.omnivault.fabric.transfer.FluidAccessFluidStorage;
import com.nightbeam.omnivault.fabric.transfer.FluidStorageAccess;
import com.nightbeam.omnivault.fabric.transfer.ItemStorageSlotAccess;
import com.nightbeam.omnivault.fabric.transfer.NetworkItemStorage;
import com.nightbeam.omnivault.fabric.transfer.SlotAccessItemStorage;
import com.nightbeam.omnivault.platform.services.IPlatformHelper;
import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.SlotAccess;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

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
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /**
     * Menus read their opening data from a buffer, as on NeoForge. Fabric sends one typed value, so the
     * buffer travels as its raw bytes and is rebuilt on the client.
     */
    @Override
    public <T extends AbstractContainerMenu> MenuType<T> createMenuType(MenuFactory<T> factory) {
        return new ExtendedScreenHandlerType<>((int containerId, Inventory inventory, byte[] data) -> {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), inventory.player.registryAccess());
            try {
                return factory.create(containerId, inventory, buffer);
            } finally {
                buffer.release();
            }
        }, ByteBufCodecs.BYTE_ARRAY);
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory::create, blocks).build(null);
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return FabricItemGroup.builder();
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraData) {
        player.openMenu(new ExtendedScreenHandlerFactory<byte[]>() {
            @Override
            public byte[] getScreenOpeningData(ServerPlayer target) {
                RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), target.registryAccess());
                try {
                    extraData.accept(buffer);
                    byte[] bytes = new byte[buffer.readableBytes()];
                    buffer.readBytes(bytes);
                    return bytes;
                } finally {
                    buffer.release();
                }
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return provider.createMenu(containerId, inventory, opener);
            }

            @Override
            public boolean shouldCloseCurrentScreen() {
                return provider.shouldCloseCurrentScreen();
            }
        });
    }

    @Override
    public Optional<SlotAccess> findSlotAccess(Level level, BlockPos pos, @Nullable Direction side) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);
        if (storage == null && side != null) {
            storage = ItemStorage.SIDED.find(level, pos, null);
        }
        if (storage == null) {
            return Optional.empty();
        }
        if (storage instanceof SlotAccessItemStorage ours) {
            return Optional.of(ours.access());
        }
        if (storage instanceof NetworkItemStorage ours) {
            return Optional.of(ours.access());
        }
        ItemStorageSlotAccess access = new ItemStorageSlotAccess(storage);
        return access.getSlots() > 0 ? Optional.of(access) : Optional.empty();
    }

    @Override
    public Optional<FluidAccess> findFluidAccess(Level level, BlockPos pos, @Nullable Direction side) {
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, side);
        if (storage == null && side != null) {
            storage = FluidStorage.SIDED.find(level, pos, null);
        }
        if (storage == null) {
            return Optional.empty();
        }
        if (storage instanceof FluidAccessFluidStorage ours) {
            return Optional.of(ours.access());
        }
        FluidStorageAccess access = new FluidStorageAccess(storage);
        return access.getTanks() > 0 ? Optional.of(access) : Optional.empty();
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        // Fake players from other mods (and test players) have no modded connection to talk to.
        if (player.connection != null && ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
