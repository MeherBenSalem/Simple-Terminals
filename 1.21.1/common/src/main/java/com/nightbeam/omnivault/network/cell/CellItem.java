package com.nightbeam.omnivault.network.cell;

/** Anything that goes in a drive bay: item cells and fluid cells. */
public interface CellItem {
    CellTier tier();

    /** Capacity in items, or millibuckets for fluid cells. */
    long capacity();

    int typeLimit();
}
