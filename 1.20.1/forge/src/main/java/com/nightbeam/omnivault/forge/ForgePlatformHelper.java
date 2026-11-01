package com.nightbeam.omnivault.forge;

import com.nightbeam.omnivault.packet.Payload;
import com.nightbeam.omnivault.platform.services.IPlatformHelper;
import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public class ForgePlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Forge";
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
        return IForgeMenuType.create(factory::create);
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
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> extraData) {
        NetworkHooks.openScreen(player, provider, extraData);
    }

    @Override
    public Optional<SlotAccess> findSlotAccess(Level level, BlockPos pos, @Nullable Direction side) {
        IItemHandler handler = capability(level, pos, side, ForgeCapabilities.ITEM_HANDLER);
        if (handler == null && side != null) {
            // Blocks that hold items without a block entity, like the composter.
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof WorldlyContainerHolder holder) {
                handler = new SidedInvWrapper(holder.getContainer(state, level, pos), side);
            }
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
    public Optional<FluidAccess> findFluidAccess(Level level, BlockPos pos, @Nullable Direction side) {
        IFluidHandler handler = capability(level, pos, side, ForgeCapabilities.FLUID_HANDLER);
        if (handler == null) {
            // Cauldrons have no block entity; later loaders expose them as one-bucket tanks.
            return Optional.ofNullable(CauldronFluidAccess.of(level, pos));
        }
        if (handler.getTanks() <= 0) {
            return Optional.empty();
        }
        if (handler instanceof FluidAccessHandler ours) {
            return Optional.of(ours.access());
        }
        return Optional.of(new FluidHandlerAccess(handler));
    }

    /** The capability on {@code side}, falling back to the block entity's unsided one. */
    @Nullable
    private static <T> T capability(Level level, BlockPos pos, @Nullable Direction side, Capability<T> capability) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        T found = blockEntity.getCapability(capability, side).resolve().orElse(null);
        if (found == null && side != null) {
            found = blockEntity.getCapability(capability, null).resolve().orElse(null);
        }
        return found;
    }

    @Override
    public double blockInteractionRange(Player player) {
        return player.getBlockReach();
    }

    @Override
    public void sendToServer(Payload payload) {
        ForgeNetworking.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Payload payload) {
        ForgeNetworking.sendToPlayer(player, payload);
    }
}
