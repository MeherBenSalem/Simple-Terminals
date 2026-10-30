package com.nightbeam.omnivault.backpack;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A slotted inventory that can grow (when a backpack is upgraded to a bigger tier) and reports every
 * change to its owner so the saved data is marked dirty.
 */
public class BackpackInventory implements Container {
    private NonNullList<ItemStack> items;
    private final Predicate<ItemStack> rule;
    private final int maxStack;
    private final Runnable onChange;

    public BackpackInventory(int size, Predicate<ItemStack> rule, int maxStack, Runnable onChange) {
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
        this.rule = rule;
        this.maxStack = maxStack;
        this.onChange = onChange;
    }

    /** Grows to at least {@code size} slots, keeping every stack where it is. Never shrinks. */
    public void ensureSize(int size) {
        if (size <= items.size()) {
            return;
        }
        NonNullList<ItemStack> grown = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            grown.set(i, items.get(i));
        }
        items = grown;
        setChanged();
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int usedSlots() {
        int used = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) {
            return;
        }
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return rule.test(stack);
    }

    public boolean accepts(ItemStack stack) {
        return rule.test(stack);
    }

    @Override
    public void setChanged() {
        onChange.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    /**
     * Inserts as much of {@code stack} as fits: first onto matching stacks, then into empty slots.
     *
     * @return what did not fit (a new stack; {@code stack} itself is not modified)
     */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !rule.test(stack)) {
            return stack.copy();
        }
        int remaining = stack.getCount();
        int limit = Math.min(maxStack, stack.getMaxStackSize());
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = 0; slot < items.size() && remaining > 0; slot++) {
                ItemStack existing = items.get(slot);
                if (pass == 0 ? !ItemStack.isSameItemSameComponents(existing, stack) : !existing.isEmpty()) {
                    continue;
                }
                int space = limit - existing.getCount();
                if (space <= 0) {
                    continue;
                }
                int moved = Math.min(space, remaining);
                if (!simulate) {
                    if (existing.isEmpty()) {
                        items.set(slot, stack.copyWithCount(moved));
                    } else {
                        existing.grow(moved);
                    }
                }
                remaining -= moved;
            }
        }
        if (!simulate && remaining != stack.getCount()) {
            setChanged();
        }
        return remaining <= 0 ? ItemStack.EMPTY : stack.copyWithCount(remaining);
    }

    /** Counts the items matching {@code stack} (same item and components). */
    public int count(ItemStack stack) {
        int total = 0;
        for (ItemStack existing : items) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                total += existing.getCount();
            }
        }
        return total;
    }

    /** Removes up to {@code amount} items matching {@code stack}, from the last slot backwards. */
    public ItemStack extract(ItemStack stack, int amount) {
        int remaining = amount;
        for (int slot = items.size() - 1; slot >= 0 && remaining > 0; slot--) {
            ItemStack existing = items.get(slot);
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int taken = Math.min(remaining, existing.getCount());
                existing.shrink(taken);
                if (existing.isEmpty()) {
                    items.set(slot, ItemStack.EMPTY);
                }
                remaining -= taken;
            }
        }
        if (remaining != amount) {
            setChanged();
        }
        return remaining == amount ? ItemStack.EMPTY : stack.copyWithCount(amount - remaining);
    }

    /** A deep copy of every slot, for {@link #restore}. */
    public List<ItemStack> snapshot() {
        List<ItemStack> copy = new ArrayList<>(items.size());
        items.forEach(stack -> copy.add(stack.copy()));
        return copy;
    }

    /** Puts back a {@link #snapshot()} taken from this inventory. */
    public void restore(List<ItemStack> snapshot) {
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, slot < snapshot.size() ? snapshot.get(slot).copy() : ItemStack.EMPTY);
        }
        setChanged();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Size", items.size());
        ContainerHelper.saveAllItems(tag, items, true, registries);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        int size = Math.max(items.size(), tag.getInt("Size"));
        items = NonNullList.withSize(size, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
