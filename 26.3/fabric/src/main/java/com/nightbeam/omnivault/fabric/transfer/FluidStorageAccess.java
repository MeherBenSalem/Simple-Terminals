package com.nightbeam.omnivault.fabric.transfer;

import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.FluidKey;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.nightbeam.omnivault.fabric.transfer.FluidAccessFluidStorage.DROPLETS_PER_MB;

/** Another mod's (or vanilla's) Fabric fluid storage seen as our tanks, in millibuckets. */
public final class FluidStorageAccess implements FluidAccess {
    private final Storage<FluidVariant> storage;

    public FluidStorageAccess(Storage<FluidVariant> storage) {
        this.storage = storage;
    }

    private List<StorageView<FluidVariant>> views() {
        List<StorageView<FluidVariant>> views = new ArrayList<>();
        for (StorageView<FluidVariant> view : storage) {
            views.add(view);
        }
        return views;
    }

    @Nullable
    private StorageView<FluidVariant> view(int tank) {
        List<StorageView<FluidVariant>> views = views();
        return tank >= 0 && tank < views.size() ? views.get(tank) : null;
    }

    @Override
    public boolean joinsTransactions() {
        return true;
    }

    @Override
    public int getTanks() {
        return views().size();
    }

    @Nullable
    @Override
    public FluidKey getFluid(int tank) {
        StorageView<FluidVariant> view = view(tank);
        return view == null || view.isResourceBlank() || view.getAmount() < DROPLETS_PER_MB ? null : FluidKey.of(view.getResource().getFluid());
    }

    @Override
    public long getAmount(int tank) {
        StorageView<FluidVariant> view = view(tank);
        return view == null || view.isResourceBlank() ? 0 : view.getAmount() / DROPLETS_PER_MB;
    }

    @Override
    public long getCapacity(int tank) {
        StorageView<FluidVariant> view = view(tank);
        return view == null ? 0 : view.getCapacity() / DROPLETS_PER_MB;
    }

    @Override
    public long fill(FluidKey key, long amount, boolean simulate) {
        return move(FluidVariant.of(key.fluid()), amount, simulate, true);
    }

    @Override
    public long drain(FluidKey key, long amount, boolean simulate) {
        // Drain the exact variant the storage holds, components and all.
        for (StorageView<FluidVariant> view : views()) {
            if (!view.isResourceBlank() && view.getResource().getFluid() == key.fluid()) {
                return move(view.getResource(), amount, simulate, false);
            }
        }
        return 0;
    }

    /** Moves whole millibuckets only: finds out how many droplets would move, then rounds down. */
    private long move(FluidVariant variant, long millibuckets, boolean simulate, boolean fill) {
        if (millibuckets <= 0 || ItemStorageSlotAccess.closing()) {
            return 0;
        }
        long wanted = millibuckets * DROPLETS_PER_MB;
        long possible;
        try (Transaction test = ItemStorageSlotAccess.open()) {
            possible = fill ? storage.insert(variant, wanted, test) : storage.extract(variant, wanted, test);
        }
        long whole = possible / DROPLETS_PER_MB;
        if (simulate || whole == 0) {
            return whole;
        }
        try (Transaction transaction = ItemStorageSlotAccess.open()) {
            long moved = fill ? storage.insert(variant, whole * DROPLETS_PER_MB, transaction) : storage.extract(variant, whole * DROPLETS_PER_MB, transaction);
            if (moved != whole * DROPLETS_PER_MB) {
                return 0;
            }
            transaction.commit();
        }
        return whole;
    }
}
