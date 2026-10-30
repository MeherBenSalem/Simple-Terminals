package com.nightbeam.omnivault.neoforge;

import com.nightbeam.omnivault.platform.services.IPlatformHelper;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public class NeoForgePlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> createMenuType(MenuFactory<T> factory) {
        return IMenuTypeExtension.create(factory::create);
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory::create, blocks).build(null);
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraData) {
        player.openMenu(provider, extraData);
    }

    @Override
    public Optional<SlotAccess> findSlotAccess(Level level, BlockPos pos, @Nullable Direction side) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler == null && side != null) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        }
        if (handler == null || handler.getSlots() <= 0) {
            return Optional.empty();
        }
        if (handler instanceof SlotAccessItemHandler ours) {
            return Optional.of(ours.access());
        }
        return Optional.of(new ItemHandlerSlotAccess(handler));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
