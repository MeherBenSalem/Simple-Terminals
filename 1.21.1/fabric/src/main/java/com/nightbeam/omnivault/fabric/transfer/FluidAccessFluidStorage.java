package com.nightbeam.omnivault.fabric.transfer;

import com.nightbeam.omnivault.storage.ChangeJournal;
import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.FluidKey;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Our tanks (the Network Port's view of the network's fluids) as Fabric fluid storage. Changes apply
 * at once inside the caller's transaction; the network records how to undo the part that went to our
 * own storage (see {@link ChangeJournal}), and other mods' storage rolls back by itself. Fabric
 * counts droplets (81 per millibucket); only whole millibuckets move.
 */
public final class FluidAccessFluidStorage extends SnapshotParticipant<Long> implements Storage<FluidVariant> {
    public static final long DROPLETS_PER_MB = 81;

    private final FluidAccess access;
    private final ChangeJournal journal = new ChangeJournal();

    public FluidAccessFluidStorage(FluidAccess access) {
        this.access = access;
    }

    public FluidAccess access() {
        return access;
    }

    /** Our fluids carry no components: a variant with components has no key here. */
    @Nullable
    static FluidKey key(FluidVariant variant) {
        return variant.isBlank() || !variant.getComponents().isEmpty() ? null : FluidKey.of(variant.getFluid());
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        FluidKey key = key(resource);
        long millibuckets = maxAmount / DROPLETS_PER_MB;
        if (key == null || millibuckets == 0) {
            return 0;
        }
        updateSnapshots(transaction);
        return journal.record(() -> access.fill(key, millibuckets, false)) * DROPLETS_PER_MB;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        FluidKey key = key(resource);
        long millibuckets = maxAmount / DROPLETS_PER_MB;
        if (key == null || millibuckets == 0) {
            return 0;
        }
        updateSnapshots(transaction);
        return journal.record(() -> access.drain(key, millibuckets, false)) * DROPLETS_PER_MB;
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        List<StorageView<FluidVariant>> views = new ArrayList<>();
        for (int tank = 0; tank < access.getTanks(); tank++) {
            views.add(new Tank(tank));
        }
        return views.iterator();
    }

    @Override
    protected Long createSnapshot() {
        return journal.mark();
    }

    @Override
    protected void readSnapshot(Long mark) {
        journal.rollBackTo(mark);
    }

    @Override
    protected void onFinalCommit() {
        journal.commit();
    }

    private final class Tank implements StorageView<FluidVariant> {
        private final int tank;

        Tank(int tank) {
            this.tank = tank;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            FluidKey here = access.getFluid(tank);
            return here != null && here.equals(key(resource)) ? FluidAccessFluidStorage.this.extract(resource, maxAmount, transaction) : 0;
        }

        @Override
        public boolean isResourceBlank() {
            return access.getFluid(tank) == null;
        }

        @Override
        public FluidVariant getResource() {
            FluidKey key = access.getFluid(tank);
            return key == null ? FluidVariant.blank() : FluidVariant.of(key.fluid());
        }

        @Override
        public long getAmount() {
            return access.getFluid(tank) == null ? 0 : access.getAmount(tank) * DROPLETS_PER_MB;
        }

        @Override
        public long getCapacity() {
            return access.getCapacity(tank) * DROPLETS_PER_MB;
        }
    }
}
