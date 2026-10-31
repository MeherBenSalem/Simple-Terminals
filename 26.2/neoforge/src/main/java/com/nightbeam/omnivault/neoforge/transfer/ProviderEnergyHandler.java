package com.nightbeam.omnivault.neoforge.transfer;

import com.nightbeam.omnivault.registry.ModCapabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** One of our energy buffers (the Nexus Core) as a NeoForge energy handler. Insert-only; accepted FE is added when the transaction commits. */
public final class ProviderEnergyHandler extends SnapshotJournal<Long> implements EnergyHandler {
    private final ModCapabilities.EnergyProvider provider;
    private long pending;

    public ProviderEnergyHandler(ModCapabilities.EnergyProvider provider) {
        this.provider = provider;
    }

    @Override
    public long getAmountAsLong() {
        return provider.getEnergyStored();
    }

    @Override
    public long getCapacityAsLong() {
        return provider.getMaxEnergyStored();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        if (amount <= 0 || !provider.canReceiveEnergy()) {
            return 0;
        }
        int accepted = (int) Math.min(amount, Math.max(0, provider.receiveEnergy(Transactions.clamp(pending + amount), true) - pending));
        if (accepted > 0) {
            updateSnapshots(transaction);
            pending += accepted;
        }
        return accepted;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    protected Long createSnapshot() {
        return pending;
    }

    @Override
    protected void revertToSnapshot(Long snapshot) {
        pending = snapshot;
    }

    @Override
    protected void onRootCommit(Long originalState) {
        int amount = Transactions.clamp(pending);
        pending = 0;
        provider.receiveEnergy(amount, false);
    }
}
