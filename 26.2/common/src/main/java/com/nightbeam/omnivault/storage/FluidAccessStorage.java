package com.nightbeam.omnivault.storage;

import java.util.function.ObjLongConsumer;

/** Exposes any block's tanks as counted fluid storage. */
public class FluidAccessStorage implements FluidStorage {
    private final FluidAccess access;

    public FluidAccessStorage(FluidAccess access) {
        this.access = access;
    }

    @Override
    public boolean joinsTransactions() {
        return access.joinsTransactions();
    }

    @Override
    public long insert(FluidKey key, long amount, boolean simulate) {
        return access.fill(key, amount, simulate);
    }

    @Override
    public long extract(FluidKey key, long amount, boolean simulate) {
        return access.drain(key, amount, simulate);
    }

    @Override
    public void forEach(ObjLongConsumer<FluidKey> consumer) {
        for (int tank = 0; tank < access.getTanks(); tank++) {
            FluidKey key = access.getFluid(tank);
            long amount = access.getAmount(tank);
            if (key != null && amount > 0) {
                consumer.accept(key, amount);
            }
        }
    }
}
