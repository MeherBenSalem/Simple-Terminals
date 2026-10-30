package com.nightbeam.omnivault.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Base menu: adds the player inventory and implements shift-click between the menu's own slots
 * ({@code 0 .. ownSlots-1}) and the player inventory, which is always added last.
 */
public abstract class OVMenu extends AbstractContainerMenu {
    protected int ownSlots;
    protected int playerStart = -1;

    protected OVMenu(@Nullable MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    /** Adds the 27 + 9 player slots with the main grid's top-left slot at (x, y). */
    protected void addPlayerInventory(Inventory inventory, int x, int y) {
        ownSlots = slots.size();
        playerStart = ownSlots;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, x + column * 18, y + 58));
        }
    }

    protected int playerEnd() {
        return playerStart + 36;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, playerEnd(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveIntoOwnSlots(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /** Moves a stack from the player inventory into this menu's own slots. */
    protected boolean moveIntoOwnSlots(ItemStack stack) {
        return moveItemStackTo(stack, 0, ownSlots, false);
    }
}
