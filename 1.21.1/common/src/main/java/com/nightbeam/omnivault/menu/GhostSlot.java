package com.nightbeam.omnivault.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

/**
 * A filter slot: clicking with an item records a copy of it, clicking empty-handed clears it.
 * Nothing is ever taken from or given to the player. Handled by {@link OVMenu#clicked}.
 */
public class GhostSlot extends Slot {
    public GhostSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
