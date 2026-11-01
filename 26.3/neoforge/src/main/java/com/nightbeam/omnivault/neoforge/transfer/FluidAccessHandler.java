package com.nightbeam.omnivault.neoforge.transfer;

import com.nightbeam.omnivault.storage.ChangeJournal;
import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.FluidKey;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Our tanks (the Network Port's view of the network's fluids) as a NeoForge fluid handler, in
 * millibuckets. Changes apply at once inside the caller's transaction; the network records how to undo
 * the part that went to our own storage (see {@link ChangeJournal}), and other mods' storage rolls back
 * by itself.
 */
public final class FluidAccessHandler extends SnapshotJournal<Long> implements ResourceHandler<FluidResource> {
    private final FluidAccess access;
    private final ChangeJournal journal = new ChangeJournal();

    public FluidAccessHandler(FluidAccess access) {
        this.access = access;
    }

    public FluidAccess access() {
        return access;
    }

    /** Our fluids carry no components: a resource with components has no key here. */
    @Nullable
    static FluidKey key(FluidResource resource) {
        return resource.isEmpty() || !resource.isComponentsPatchEmpty() ? null : FluidKey.of(resource.getFluid());
    }

    @Override
    public int size() {
        return access.getTanks();
    }

    @Override
    public FluidResource getResource(int index) {
        FluidKey key = access.getFluid(index);
        return key == null ? FluidResource.EMPTY : FluidResource.of(key.fluid());
    }

    @Override
    public long getAmountAsLong(int index) {
        return access.getFluid(index) == null ? 0 : access.getAmount(index);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return access.getCapacity(index);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return key(resource) != null;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return insert(resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        FluidKey here = access.getFluid(index);
        return here != null && here.equals(key(resource)) ? extract(resource, amount, transaction) : 0;
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        FluidKey key = key(resource);
        if (key == null || amount == 0) {
            return 0;
        }
        updateSnapshots(transaction);
        return Transactions.clamp(journal.record(() -> access.fill(key, amount, false)));
    }

    @Override
    public int extract(FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        FluidKey key = key(resource);
        if (key == null || amount == 0) {
            return 0;
        }
        updateSnapshots(transaction);
        return Transactions.clamp(journal.record(() -> access.drain(key, amount, false)));
    }

    @Override
    protected Long createSnapshot() {
        return journal.mark();
    }

    @Override
    protected void revertToSnapshot(Long mark) {
        journal.rollBackTo(mark);
    }

    @Override
    protected void onRootCommit(Long originalState) {
        journal.commit();
    }
}
