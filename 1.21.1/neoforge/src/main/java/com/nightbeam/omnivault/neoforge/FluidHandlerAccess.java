package com.nightbeam.omnivault.neoforge;

import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.FluidKey;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Another mod's (or vanilla's) fluid handler seen through the common API. */
public record FluidHandlerAccess(IFluidHandler handler) implements FluidAccess {
    static int clamp(long amount) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, amount));
    }

    @Override
    public int getTanks() {
        return handler.getTanks();
    }

    @Override
    public FluidKey getFluid(int tank) {
        FluidStack stack = handler.getFluidInTank(tank);
        return stack.isEmpty() ? null : FluidKey.of(stack.getFluid());
    }

    @Override
    public long getAmount(int tank) {
        return handler.getFluidInTank(tank).getAmount();
    }

    @Override
    public long getCapacity(int tank) {
        return handler.getTankCapacity(tank);
    }

    @Override
    public long fill(FluidKey key, long amount, boolean simulate) {
        return handler.fill(new FluidStack(key.fluid(), clamp(amount)), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public long drain(FluidKey key, long amount, boolean simulate) {
        FluidStack drained = handler.drain(new FluidStack(key.fluid(), clamp(amount)), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        return drained.isEmpty() ? 0 : drained.getAmount();
    }
}
