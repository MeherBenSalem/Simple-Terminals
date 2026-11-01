package com.nightbeam.omnivault.fabric.transfer;

import com.nightbeam.omnivault.fabric.compat.RebornEnergyCompat;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.ItemStorage;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/** Registers our block entities' inventories, tanks and energy buffers with Fabric's lookups. */
public final class FabricStorageExposure {
    private FabricStorageExposure() {
    }

    public static void register() {
        for (Supplier<? extends BlockEntityType<? extends ModCapabilities.ItemProvider>> type : ModCapabilities.itemProviders()) {
            registerItems(type.get());
        }
        for (Supplier<? extends BlockEntityType<? extends ModCapabilities.FluidProvider>> type : ModCapabilities.fluidProviders()) {
            registerFluids(type.get());
        }
        if (FabricLoader.getInstance().isModLoaded(RebornEnergyCompat.MOD_ID)) {
            RebornEnergyCompat.register();
        }
    }

    private static <T extends BlockEntity & ModCapabilities.ItemProvider> void registerItems(BlockEntityType<?> type) {
        @SuppressWarnings("unchecked")
        BlockEntityType<T> typed = (BlockEntityType<T>) type;
        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlockEntity((blockEntity, side) -> {
            SlotAccess access = blockEntity.getSlotAccess(side);
            if (access == null) {
                return null;
            }
            ItemStorage network = access.networkStorage();
            return network != null
                    ? new NetworkItemStorage(access, network)
                    : new SlotAccessItemStorage(access, stack -> drop(blockEntity, stack));
        }, typed);
    }

    private static <T extends BlockEntity & ModCapabilities.FluidProvider> void registerFluids(BlockEntityType<?> type) {
        @SuppressWarnings("unchecked")
        BlockEntityType<T> typed = (BlockEntityType<T>) type;
        FluidStorage.SIDED.registerForBlockEntity((blockEntity, side) -> {
            FluidAccess access = blockEntity.getFluidAccess(side);
            return access == null ? null : new FluidAccessFluidStorage(access);
        }, typed);
    }

    private static void drop(BlockEntity blockEntity, ItemStack stack) {
        Level level = blockEntity.getLevel();
        if (level != null && !stack.isEmpty()) {
            BlockPos pos = blockEntity.getBlockPos();
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
        }
    }
}
