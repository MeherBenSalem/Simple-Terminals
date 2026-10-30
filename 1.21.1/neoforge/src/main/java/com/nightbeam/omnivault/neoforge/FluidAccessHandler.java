package com.nightbeam.omnivault.neoforge;

import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.FluidKey;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** One of our fluid stores exposed as a NeoForge fluid handler capability. */
public record FluidAccessHandler(FluidAccess access) implements IFluidHandler {
    @Override
    public int getTanks() {
        return access.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        FluidKey key = access.getFluid(tank);
        long amount = access.getAmount(tank);
        return key == null || amount <= 0 ? FluidStack.EMPTY : new FluidStack(key.fluid(), FluidHandlerAccess.clamp(amount));
    }

    @Override
    public int getTankCapacity(int tank) {
        return FluidHandlerAccess.clamp(access.getCapacity(tank));
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        FluidKey key = resource.isEmpty() ? null : FluidKey.of(resource.getFluid());
        return key == null ? 0 : FluidHandlerAccess.clamp(access.fill(key, resource.getAmount(), action.simulate()));
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        FluidKey key = resource.isEmpty() ? null : FluidKey.of(resource.getFluid());
        if (key == null) {
            return FluidStack.EMPTY;
        }
        long drained = access.drain(key, resource.getAmount(), action.simulate());
        return drained <= 0 ? FluidStack.EMPTY : new FluidStack(key.fluid(), FluidHandlerAccess.clamp(drained));
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (int tank = 0; tank < access.getTanks(); tank++) {
            FluidKey key = access.getFluid(tank);
            if (key != null && access.getAmount(tank) > 0) {
                return drain(new FluidStack(key.fluid(), maxDrain), action);
            }
        }
        return FluidStack.EMPTY;
    }
}
