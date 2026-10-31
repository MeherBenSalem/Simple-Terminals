package com.nightbeam.omnivault.backpack.upgrade;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** What a backpack upgrade card does, and which settings its tab in the backpack screen shows. */
public enum UpgradeType implements StringRepresentable {
    /** Picked-up items that match the filter go straight into the backpack. */
    PICKUP(true, 0, false, 0x5BE08A),
    /** Pulls matching item entities (and experience) within range to the wearer. */
    MAGNET(true, 0, true, 0xFF6B6B),
    /** Destroys matching items on pickup; mode 1 only destroys what does not fit in the backpack. */
    VOID(true, 2, false, 0xA46BFF),
    /** Eats food from the backpack when hungry, never food with harmful effects. */
    FEEDING(true, 0, false, 0xF2C14E),
    /** Tops up hotbar and off-hand stacks from the backpack. */
    RESTOCK(true, 0, false, 0x38E1FF),
    /** Crafts 3x3 (mode 0 also 2x2) compressible items into their blocks; mode 1 is 3x3 only. */
    COMPACTING(true, 2, false, 0xD9874E),
    /** Adds a 3x3 crafting grid tab to the backpack screen. */
    CRAFTING(false, 0, false, 0xC8CFD8),
    /** The dropped backpack never despawns, burns or gets destroyed. */
    EVERLASTING(false, 0, false, 0xE0FDFF),
    /** Sneak-right-click a container with the backpack to dump matching items into it; mode 1 only moves items it already holds. */
    DEPOSIT(true, 2, false, 0x4DE8E0);

    private final boolean filter;
    private final int modes;
    private final boolean range;
    private final int color;

    UpgradeType(boolean filter, int modes, boolean range, int color) {
        this.filter = filter;
        this.modes = modes;
        this.range = range;
        this.color = color;
    }

    public boolean hasFilter() {
        return filter;
    }

    /** Number of selectable modes (0 = none). */
    public int modes() {
        return modes;
    }

    public boolean hasRange() {
        return range;
    }

    /** Whether the backpack screen shows a tab for this upgrade. */
    public boolean hasTab() {
        return this != EVERLASTING;
    }

    /** RGB accent used for this upgrade's tab. */
    public int color() {
        return color;
    }

    /** Registry path of the upgrade card item, e.g. {@code pickup_upgrade}. */
    public String itemId() {
        return getSerializedName() + "_upgrade";
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
