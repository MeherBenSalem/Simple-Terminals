package com.nightbeam.omnivault.physical;

import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Any vanilla {@link Container} seen through the loader-neutral {@link SlotAccess} contract (what
 * hoppers, pipes and the storage network use). The container is resolved on every call so a live view
 * (for example a Linked Vault whose frequency changes) never goes stale in a capability cache.
 */
public final class ContainerSlotAccess implements SlotAccess {
    private final Supplier<? extends Container> container;

    public ContainerSlotAccess(Supplier<? extends Container> container) {
        this.container = container;
    }

    public static ContainerSlotAccess of(Container container) {
        return new ContainerSlotAccess(() -> container);
    }

    @Override
    public int getSlots() {
        return container.get().getContainerSize();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        Container c = container.get();
        return slot >= 0 && slot < c.getContainerSize() ? c.getItem(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        Container c = container.get();
        if (stack.isEmpty() || slot < 0 || slot >= c.getContainerSize() || !c.canPlaceItem(slot, stack)) {
            return stack;
        }
        ItemStack existing = c.getItem(slot);
        int limit = c.getMaxStackSize(stack);
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                return stack;
            }
            limit -= existing.getCount();
        }
        if (limit <= 0) {
            return stack;
        }
        int moved = Math.min(limit, stack.getCount());
        if (!simulate) {
            if (existing.isEmpty()) {
                c.setItem(slot, stack.copyWithCount(moved));
            } else {
                existing.grow(moved);
                c.setChanged();
            }
        }
        return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        Container c = container.get();
        if (amount <= 0 || slot < 0 || slot >= c.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = c.getItem(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int count = Math.min(amount, Math.min(existing.getCount(), existing.getMaxStackSize()));
        if (simulate) {
            return existing.copyWithCount(count);
        }
        ItemStack removed = c.removeItem(slot, count);
        c.setChanged();
        return removed;
    }

    @Override
    public int getSlotLimit(int slot) {
        return container.get().getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return container.get().canPlaceItem(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        Container c = container.get();
        c.setItem(slot, stack);
        c.setChanged();
    }
}
