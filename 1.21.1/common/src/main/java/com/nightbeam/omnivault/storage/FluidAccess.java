package com.nightbeam.omnivault.storage;

/**
 * A block's fluid tanks, mirroring the loader fluid-handler contract (fill returns the amount
 * accepted, drain the amount removed). Loaders adapt their own handler types to and from this.
 */
public interface FluidAccess {
    int getTanks();

    /** The fluid in a tank, or null when it is empty. */
    FluidKey getFluid(int tank);

    long getAmount(int tank);

    long getCapacity(int tank);

    long fill(FluidKey key, long amount, boolean simulate);

    long drain(FluidKey key, long amount, boolean simulate);

    /** True for another mod's tanks that take part in loader transactions (see {@link ItemStorage#joinsTransactions()}). */
    default boolean joinsTransactions() {
        return false;
    }
}
