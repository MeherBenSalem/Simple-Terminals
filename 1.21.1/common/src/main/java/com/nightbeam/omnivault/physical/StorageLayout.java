package com.nightbeam.omnivault.physical;

/**
 * Slot positions for a storage grid of {@code columns × rows} above the player inventory. Wide grids are
 * used instead of scrolling (vanilla slot positions are final). Menus and screens share this so they
 * always agree.
 */
public record StorageLayout(int columns, int rows) {
    public static final int GRID_Y = 18;

    public int size() {
        return columns * rows;
    }

    public int width() {
        return Math.max(176, columns * 18 + 14);
    }

    /** Item position of the grid's top-left slot. */
    public int gridX() {
        return (width() - columns * 18) / 2 + 1;
    }

    public int gridY() {
        return GRID_Y;
    }

    /** Tall grids drop the "Inventory" label so the screen still fits a 270px-high GUI. */
    public boolean compact() {
        return rows >= 8;
    }

    public int inventoryX() {
        return (width() - 162) / 2 + 1;
    }

    public int inventoryY() {
        return gridY() + rows * 18 + (compact() ? 7 : 14);
    }

    public int height() {
        return inventoryY() + 82;
    }
}
