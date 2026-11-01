package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.world.item.ItemStack;

/** Inserting into a slotted inventory slot by slot, the way automation does it. */
final class TestInventories {
    private TestInventories() {
    }

    /** Fills slots in order; returns what didn't fit. */
    static ItemStack insert(SlotAccess access, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int slot = 0; slot < access.getSlots() && !rest.isEmpty(); slot++) {
            rest = access.insertItem(slot, rest, false);
        }
        return rest;
    }

    /** Tops up stacks of the same item first, then uses empty slots; returns what didn't fit. */
    static ItemStack insertStacked(SlotAccess access, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int slot = 0; slot < access.getSlots() && !rest.isEmpty(); slot++) {
            if (ItemStack.isSameItemSameComponents(access.getStackInSlot(slot), rest)) {
                rest = access.insertItem(slot, rest, false);
            }
        }
        for (int slot = 0; slot < access.getSlots() && !rest.isEmpty(); slot++) {
            if (access.getStackInSlot(slot).isEmpty()) {
                rest = access.insertItem(slot, rest, false);
            }
        }
        return rest;
    }
}
