package com.nightbeam.omnivault.drawer;

import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.world.item.ItemStack;

import java.util.function.ObjLongConsumer;

/**
 * A drawer seen as an inventory: one slot per compartment, whose stack may be far larger than a normal
 * stack and whose limit is the compartment's capacity. It is also counted {@link ItemStorage}, so the
 * network can use long counts without going through slots.
 */
public final class DrawerAccess implements SlotAccess, ItemStorage {
    private final AbstractDrawerBlockEntity drawer;

    DrawerAccess(AbstractDrawerBlockEntity drawer) {
        this.drawer = drawer;
    }

    public AbstractDrawerBlockEntity drawer() {
        return drawer;
    }

    @Override
    public int getSlots() {
        return drawer.compartments();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= drawer.compartments()) {
            return ItemStack.EMPTY;
        }
        ItemKey key = drawer.keyAt(slot);
        long count = drawer.countAt(slot);
        return key == null || count <= 0 ? ItemStack.EMPTY : key.toStack((int) Math.min(count, Integer.MAX_VALUE));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= drawer.compartments()) {
            return stack;
        }
        ItemKey key = ItemKey.of(stack);
        // An empty compartment does not take a type another compartment already holds: automation fills
        // the matching compartment instead of spreading one item over the whole drawer.
        if (drawer.keyAt(slot) == null && drawer.holds(key)) {
            return stack;
        }
        long accepted = drawer.insert(slot, key, stack.getCount(), simulate);
        if (accepted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        return accepted <= 0 ? stack : stack.copyWithCount(stack.getCount() - (int) accepted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= drawer.compartments()) {
            return ItemStack.EMPTY;
        }
        ItemKey key = drawer.keyAt(slot);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        long taken = drawer.extract(slot, Math.min(amount, key.maxStackSize()), simulate);
        return taken <= 0 ? ItemStack.EMPTY : key.toStack((int) taken);
    }

    @Override
    public int getSlotLimit(int slot) {
        if (slot < 0 || slot >= drawer.compartments()) {
            return 0;
        }
        return (int) Math.min(drawer.capacityAt(slot), Integer.MAX_VALUE);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty() || slot < 0 || slot >= drawer.compartments()) {
            return false;
        }
        ItemKey key = ItemKey.of(stack);
        return drawer.accepts(slot, key) && !(drawer.keyAt(slot) == null && drawer.holds(key));
    }

    // ItemStorage ------------------------------------------------------------------------------------

    @Override
    public long insert(ItemKey key, long amount, boolean simulate) {
        return drawer.insertAny(key, amount, simulate);
    }

    @Override
    public long extract(ItemKey key, long amount, boolean simulate) {
        long remaining = amount;
        for (int slot = 0; slot < drawer.compartments() && remaining > 0; slot++) {
            if (key.equals(drawer.keyAt(slot))) {
                remaining -= drawer.extract(slot, remaining, simulate);
            }
        }
        return amount - remaining;
    }

    @Override
    public void forEach(ObjLongConsumer<ItemKey> consumer) {
        for (int slot = 0; slot < drawer.compartments(); slot++) {
            ItemKey key = drawer.keyAt(slot);
            long count = drawer.countAt(slot);
            if (key != null && count > 0) {
                consumer.accept(key, count);
            }
        }
    }

    @Override
    public long getCount(ItemKey key) {
        long total = 0;
        for (int slot = 0; slot < drawer.compartments(); slot++) {
            if (key.equals(drawer.keyAt(slot))) {
                total += drawer.countAt(slot);
            }
        }
        return total;
    }

    @Override
    public boolean isPreferredFor(ItemKey key) {
        return drawer.holds(key);
    }

    @Override
    public long version() {
        return drawer.version();
    }
}
