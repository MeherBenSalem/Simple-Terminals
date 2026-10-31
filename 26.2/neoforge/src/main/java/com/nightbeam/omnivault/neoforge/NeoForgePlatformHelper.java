package com.nightbeam.omnivault.neoforge;

import com.nightbeam.omnivault.neoforge.transfer.FluidAccessHandler;
import com.nightbeam.omnivault.neoforge.transfer.FluidHandlerAccess;
import com.nightbeam.omnivault.neoforge.transfer.ItemHandlerSlotAccess;
import com.nightbeam.omnivault.neoforge.transfer.NetworkItemHandler;
import com.nightbeam.omnivault.neoforge.transfer.SlotAccessItemHandler;
import com.nightbeam.omnivault.platform.services.IPlatformHelper;
import com.nightbeam.omnivault.storage.FluidAccess;
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
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
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
        return !FMLEnvironment.isProduction();
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
        return new BlockEntityType<>(factory::create, Set.of(blocks));
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
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        if (handler == null && side != null) {
            handler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
        }
        if (handler == null || handler.size() <= 0) {
            return Optional.empty();
        }
        if (handler instanceof SlotAccessItemHandler ours) {
            return Optional.of(ours.access());
        }
        if (handler instanceof NetworkItemHandler ours) {
            return Optional.of(ours.access());
        }
        return Optional.of(new ItemHandlerSlotAccess(handler));
    }

    @Override
    public Optional<FluidAccess> findFluidAccess(Level level, BlockPos pos, @Nullable Direction side) {
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        if (handler == null && side != null) {
            handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
        }
        if (handler == null || handler.size() <= 0) {
            return Optional.empty();
        }
        if (handler instanceof FluidAccessHandler ours) {
            return Optional.of(ours.access());
        }
        return Optional.of(new FluidHandlerAccess(handler));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        // Fake players from other mods (and test players) have no modded connection to talk to.
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
