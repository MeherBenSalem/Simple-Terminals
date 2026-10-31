package com.nightbeam.omnivault.backpack;

/**
 * Pixel layout of the backpack screen, shared by the menu (slot positions) and the screen (drawing).
 * Coordinates are relative to the window's top-left corner; slot coordinates are where the item renders.
 */
public record BackpackLayout(int columns, int totalRows, int visibleRows, int totalSlots, int upgradeSlots,
                             int imageWidth, int imageHeight, int gridX, int gridY, int inventoryX, int inventoryY) {
    public static final int MAX_VISIBLE_ROWS = 6;
    public static final int SLOT = 18;
    /** Upgrade bay on the left: slot item positions. */
    public static final int BAY_WIDTH = 28;
    public static final int BAY_SLOT_X = -BAY_WIDTH + 6;
    public static final int BAY_SLOT_Y = 21;
    /** Tabs on the right edge. */
    public static final int TAB_WIDTH = 26;
    public static final int TAB_HEIGHT = 24;
    public static final int TAB_SPACING = 26;
    public static final int TAB_Y = 6;
    /** Expanded tab panels. */
    public static final int PANEL_WIDTH = 120;
    public static final int PANEL_Y = 4;
    public static final int CRAFT_GRID_DX = 9;
    public static final int CRAFT_GRID_DY = 22;
    public static final int CRAFT_RESULT_DX = CRAFT_GRID_DX + 3 * SLOT + 30;
    public static final int CRAFT_RESULT_DY = CRAFT_GRID_DY + SLOT;
    public static final int CRAFT_PANEL_HEIGHT = CRAFT_GRID_DY + 3 * SLOT + 10;

    public static BackpackLayout of(BackpackTier tier, int totalSlots, int upgradeSlots) {
        int columns = tier.columns();
        int totalRows = Math.max(1, (totalSlots + columns - 1) / columns);
        int visibleRows = Math.min(totalRows, MAX_VISIBLE_ROWS);
        boolean scrolls = totalRows > visibleRows;
        int contentWidth = columns * SLOT + (scrolls ? 14 : 0);
        int imageWidth = Math.max(176, contentWidth + 14);
        int gridX = (imageWidth - contentWidth) / 2 + 1;
        int gridY = 19;
        int inventoryY = gridY + visibleRows * SLOT + 14;
        int inventoryX = (imageWidth - 9 * SLOT) / 2 + 1;
        int imageHeight = inventoryY + 3 * SLOT + 4 + SLOT + 6;
        return new BackpackLayout(columns, totalRows, visibleRows, totalSlots, upgradeSlots, imageWidth, imageHeight, gridX, gridY, inventoryX, inventoryY);
    }

    public int visibleSlots() {
        return columns * visibleRows;
    }

    public boolean scrolls() {
        return totalRows > visibleRows;
    }

    public int maxScroll() {
        return Math.max(0, totalRows - visibleRows);
    }

    /** Where the panel of the first tab (and so the crafting grid) starts. */
    public int panelX() {
        return imageWidth - 3;
    }

    public int scrollbarX() {
        return gridX - 1 + columns * SLOT + 4;
    }

    public int bayHeight() {
        return BAY_SLOT_Y + upgradeSlots * SLOT + 6;
    }
}
