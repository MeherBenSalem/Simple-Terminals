package com.nightbeam.omnivault.neoforge.transfer;

import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.FluidKey;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/** Another mod's (or vanilla's) fluid handler seen as our tanks, in millibuckets. */
public record FluidHandlerAccess(ResourceHandler<FluidResource> handler) implements FluidAccess {
    @Override
    public boolean joinsTransactions() {
        return true;
    }

    @Override
    public int getTanks() {
        return handler.size();
    }

    @Nullable
    @Override
    public FluidKey getFluid(int tank) {
        if (tank < 0 || tank >= handler.size()) {
            return null;
        }
        FluidResource resource = handler.getResource(tank);
        return resource.isEmpty() || handler.getAmountAsLong(tank) <= 0 ? null : FluidKey.of(resource.getFluid());
    }

    @Override
    public long getAmount(int tank) {
        return tank < 0 || tank >= handler.size() ? 0 : handler.getAmountAsLong(tank);
    }

    @Override
    public long getCapacity(int tank) {
        return tank < 0 || tank >= handler.size() ? 0 : handler.getCapacityAsLong(tank, handler.getResource(tank));
    }

    @Override
    public long fill(FluidKey key, long amount, boolean simulate) {
        return move(FluidResource.of(key.fluid()), amount, simulate, true);
    }

    @Override
    public long drain(FluidKey key, long amount, boolean simulate) {
        // Drain the exact resource the handler holds, components and all.
        for (int tank = 0; tank < handler.size(); tank++) {
            FluidResource resource = handler.getResource(tank);
            if (!resource.isEmpty() && resource.getFluid() == key.fluid()) {
                return move(resource, amount, simulate, false);
            }
        }
        return 0;
    }

    private long move(FluidResource resource, long amount, boolean simulate, boolean fill) {
        if (amount <= 0 || Transactions.closing()) {
            return 0;
        }
        try (Transaction transaction = Transactions.open()) {
            int moved = fill ? handler.insert(resource, Transactions.clamp(amount), transaction)
                    : handler.extract(resource, Transactions.clamp(amount), transaction);
            if (!simulate) {
                transaction.commit();
            }
            return moved;
        }
    }
}
