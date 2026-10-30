package com.nightbeam.omnivault.registry;

import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Block entity types whose inventories or energy buffers are exposed to other mods. Each loader
 * turns these into its own capability or lookup registrations.
 */
public final class ModCapabilities {
    private static final List<Supplier<? extends BlockEntityType<? extends ItemProvider>>> ITEMS = new ArrayList<>();
    private static final List<Supplier<? extends BlockEntityType<? extends EnergyProvider>>> ENERGY = new ArrayList<>();

    private ModCapabilities() {
    }

    public static void exposeItems(Supplier<? extends BlockEntityType<? extends ItemProvider>> type) {
        ITEMS.add(type);
    }

    public static void exposeEnergy(Supplier<? extends BlockEntityType<? extends EnergyProvider>> type) {
        ENERGY.add(type);
    }

    public static List<Supplier<? extends BlockEntityType<? extends ItemProvider>>> itemProviders() {
        return Collections.unmodifiableList(ITEMS);
    }

    public static List<Supplier<? extends BlockEntityType<? extends EnergyProvider>>> energyProviders() {
        return Collections.unmodifiableList(ENERGY);
    }

    public interface ItemProvider {
        /** @return the inventory seen from {@code side} ({@code null} = internal), or null for none. */
        @Nullable
        SlotAccess getSlotAccess(@Nullable Direction side);
    }

    /** Forge-Energy-style buffer, in FE. */
    public interface EnergyProvider {
        int receiveEnergy(int amount, boolean simulate);

        int getEnergyStored();

        int getMaxEnergyStored();

        default boolean canReceiveEnergy() {
            return true;
        }

        /** Block entities return null on a side that should not expose energy. */
        default boolean exposesEnergy(@Nullable Direction side) {
            return true;
        }

        @SuppressWarnings("unused")
        default BlockEntity self() {
            return (BlockEntity) this;
        }
    }
}
