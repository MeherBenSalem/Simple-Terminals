package com.nightbeam.omnivault.network.cell;

import net.minecraft.world.item.Rarity;

/** Storage cell tiers. Capacity counts items; every tier also has a type limit (see config). */
public enum CellTier {
    T4K("4k", 4_096L, 0xFF9AA0A8, Rarity.COMMON),
    T16K("16k", 16_384L, 0xFFF5C542, Rarity.COMMON),
    T64K("64k", 65_536L, 0xFF4DE8E0, Rarity.UNCOMMON),
    T256K("256k", 262_144L, 0xFF5BE08A, Rarity.UNCOMMON),
    T1M("1m", 1_048_576L, 0xFFA46BFF, Rarity.RARE),
    T16M("16m", 16_777_216L, 0xFFFF6BD5, Rarity.EPIC),
    /** Late game: over two billion items. */
    SINGULARITY("singularity", 2_147_483_648L, 0xFFE9D7FF, Rarity.EPIC),
    CREATIVE("creative", Long.MAX_VALUE / 4, 0xFFFF4D4D, Rarity.EPIC);

    public final String id;
    public final long capacity;
    public final int color;
    public final Rarity rarity;

    CellTier(String id, long capacity, int color, Rarity rarity) {
        this.id = id;
        this.capacity = capacity;
        this.color = color;
        this.rarity = rarity;
    }

    public boolean isCreative() {
        return this == CREATIVE;
    }
}
