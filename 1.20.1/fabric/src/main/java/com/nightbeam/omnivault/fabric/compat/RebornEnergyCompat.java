package com.nightbeam.omnivault.fabric.compat;

import com.nightbeam.omnivault.registry.ModCapabilities;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import team.reborn.energy.api.EnergyStorage;

import java.util.function.Supplier;

/**
 * Energy on Fabric goes through Team Reborn Energy, the API tech mods share. This class is only
 * loaded when that mod is installed (most tech mods bundle it); without it, networks that require
 * energy can't be powered, so keep {@code requireEnergy} off.
 */
public final class RebornEnergyCompat {
    public static final String MOD_ID = "team_reborn_energy";

    private RebornEnergyCompat() {
    }

    public static void register() {
        for (Supplier<? extends BlockEntityType<? extends ModCapabilities.EnergyProvider>> type : ModCapabilities.energyProviders()) {
            registerEnergy(type.get());
        }
    }

    private static <T extends BlockEntity & ModCapabilities.EnergyProvider> void registerEnergy(BlockEntityType<?> type) {
        @SuppressWarnings("unchecked")
        BlockEntityType<T> typed = (BlockEntityType<T>) type;
        EnergyStorage.SIDED.registerForBlockEntity((blockEntity, side) -> blockEntity.exposesEnergy(side) ? new ProviderEnergy(blockEntity) : null, typed);
    }

    /** 1 E = 1 FE. Insert-only; accepted energy is added when the transaction commits. */
    private static final class ProviderEnergy extends SnapshotParticipant<Long> implements EnergyStorage {
        private final ModCapabilities.EnergyProvider provider;
        private long pending;

        ProviderEnergy(ModCapabilities.EnergyProvider provider) {
            this.provider = provider;
        }

        private static int clamp(long amount) {
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, amount));
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            if (maxAmount <= 0 || !provider.canReceiveEnergy()) {
                return 0;
            }
            long accepted = Math.min(maxAmount, Math.max(0, provider.receiveEnergy(clamp(pending + maxAmount), true) - pending));
            if (accepted > 0) {
                updateSnapshots(transaction);
                pending += accepted;
            }
            return accepted;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public long getAmount() {
            return provider.getEnergyStored();
        }

        @Override
        public long getCapacity() {
            return provider.getMaxEnergyStored();
        }

        @Override
        public boolean supportsExtraction() {
            return false;
        }

        @Override
        public boolean supportsInsertion() {
            return provider.canReceiveEnergy();
        }

        @Override
        protected Long createSnapshot() {
            return pending;
        }

        @Override
        protected void readSnapshot(Long snapshot) {
            pending = snapshot;
        }

        @Override
        protected void onFinalCommit() {
            int amount = clamp(pending);
            pending = 0;
            provider.receiveEnergy(amount, false);
        }
    }
}
