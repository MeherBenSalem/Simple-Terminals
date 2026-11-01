package com.nightbeam.omnivault.forge;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Our block entities' inventories, tanks and energy buffers as Forge capabilities. The block entities live
 * in common code and cannot override {@code getCapability}, so each one gets a provider attached as it is
 * created. The provider keeps one {@link LazyOptional} per side and invalidates them when the block entity
 * goes away or stops exposing anything on that side.
 */
final class CapabilityExposure {
    private static final ResourceLocation ID = OmniVault.id("storage");
    private static Set<BlockEntityType<?>> itemTypes;
    private static Set<BlockEntityType<?>> fluidTypes;
    private static Set<BlockEntityType<?>> energyTypes;

    private CapabilityExposure() {
    }

    static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, CapabilityExposure::attach);
    }

    private static Set<BlockEntityType<?>> types(Iterable<? extends Supplier<? extends BlockEntityType<?>>> suppliers) {
        Set<BlockEntityType<?>> types = new HashSet<>();
        suppliers.forEach(type -> types.add(type.get()));
        return types;
    }

    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (itemTypes == null) {
            itemTypes = types(ModCapabilities.itemProviders());
            fluidTypes = types(ModCapabilities.fluidProviders());
            energyTypes = types(ModCapabilities.energyProviders());
        }
        BlockEntity blockEntity = event.getObject();
        BlockEntityType<?> type = blockEntity.getType();
        ModCapabilities.ItemProvider items = itemTypes.contains(type) ? (ModCapabilities.ItemProvider) blockEntity : null;
        ModCapabilities.FluidProvider fluids = fluidTypes.contains(type) ? (ModCapabilities.FluidProvider) blockEntity : null;
        ModCapabilities.EnergyProvider energy = energyTypes.contains(type) ? (ModCapabilities.EnergyProvider) blockEntity : null;
        if (items == null && fluids == null && energy == null) {
            return;
        }
        Provider provider = new Provider(items, fluids, energy);
        event.addCapability(ID, provider);
        event.addListener(provider::invalidate);
    }

    private static final class Provider implements ICapabilityProvider {
        @Nullable
        private final ModCapabilities.ItemProvider items;
        @Nullable
        private final ModCapabilities.FluidProvider fluids;
        @Nullable
        private final ModCapabilities.EnergyProvider energy;
        private final Sides<SlotAccess, IItemHandler> itemSides = new Sides<>(SlotAccessItemHandler::new);
        private final Sides<FluidAccess, IFluidHandler> fluidSides = new Sides<>(FluidAccessHandler::new);
        private final Sides<ModCapabilities.EnergyProvider, IEnergyStorage> energySides = new Sides<>(CapabilityExposure::energy);

        Provider(@Nullable ModCapabilities.ItemProvider items, @Nullable ModCapabilities.FluidProvider fluids,
                 @Nullable ModCapabilities.EnergyProvider energy) {
            this.items = items;
            this.fluids = fluids;
            this.energy = energy;
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            if (capability == ForgeCapabilities.ITEM_HANDLER && items != null) {
                return itemSides.get(side, items.getSlotAccess(side)).cast();
            }
            if (capability == ForgeCapabilities.FLUID_HANDLER && fluids != null) {
                return fluidSides.get(side, fluids.getFluidAccess(side)).cast();
            }
            if (capability == ForgeCapabilities.ENERGY && energy != null) {
                return energySides.get(side, energy.exposesEnergy(side) ? energy : null).cast();
            }
            return LazyOptional.empty();
        }

        void invalidate() {
            itemSides.invalidate();
            fluidSides.invalidate();
            energySides.invalidate();
        }
    }

    /** One capability on each side (index 6 is the "no side" query), made when first asked for. */
    private static final class Sides<A, H> {
        private final Function<A, H> wrap;
        @SuppressWarnings("unchecked")
        private final LazyOptional<H>[] optionals = new LazyOptional[7];

        Sides(Function<A, H> wrap) {
            this.wrap = wrap;
        }

        LazyOptional<H> get(@Nullable Direction side, @Nullable A access) {
            int index = side == null ? 6 : side.get3DDataValue();
            LazyOptional<H> optional = optionals[index];
            if (access == null) {
                if (optional != null) {
                    optional.invalidate();
                    optionals[index] = null;
                }
                return LazyOptional.empty();
            }
            if (optional == null || !optional.isPresent()) {
                H handler = wrap.apply(access);
                optional = LazyOptional.of(() -> handler);
                optionals[index] = optional;
            }
            return optional;
        }

        void invalidate() {
            for (int i = 0; i < optionals.length; i++) {
                if (optionals[i] != null) {
                    optionals[i].invalidate();
                    optionals[i] = null;
                }
            }
        }
    }

    private static IEnergyStorage energy(ModCapabilities.EnergyProvider provider) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int toReceive, boolean simulate) {
                return provider.receiveEnergy(toReceive, simulate);
            }

            @Override
            public int extractEnergy(int toExtract, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return provider.getEnergyStored();
            }

            @Override
            public int getMaxEnergyStored() {
                return provider.getMaxEnergyStored();
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return provider.canReceiveEnergy();
            }
        };
    }
}
