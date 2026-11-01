package com.nightbeam.omnivault.util;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Crafting grids for recipe lookups outside a crafting menu (compacting, patterns, previews). Minecraft
 * 1.20.1 matches crafting recipes against a {@link CraftingContainer}, which needs a menu to notify; these
 * grids notify nobody.
 */
public final class CraftingGrid {
    private static final AbstractContainerMenu NO_MENU = new AbstractContainerMenu(null, -1) {
        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return false;
        }

        @Override
        public void slotsChanged(net.minecraft.world.Container container) {
        }
    };

    private CraftingGrid() {
    }

    /** A {@code width}×{@code height} grid holding {@code items} (row by row; missing entries are empty). */
    public static CraftingContainer of(int width, int height, List<ItemStack> items) {
        NonNullList<ItemStack> grid = NonNullList.withSize(width * height, ItemStack.EMPTY);
        for (int i = 0; i < grid.size() && i < items.size(); i++) {
            grid.set(i, items.get(i));
        }
        return new TransientCraftingContainer(NO_MENU, width, height, grid);
    }
}
