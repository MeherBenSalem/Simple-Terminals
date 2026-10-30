package com.nightbeam.omnivault.physical.trash;

import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Trash can state: the last voided stack (kept until the next item replaces it, so a mistake can be
 * undone from the GUI) and an optional automation filter. Players can always trash anything through
 * the GUI; the filter only decides what pipes and hoppers may void.
 */
public class TrashInventory {
    public static final int FILTER_SLOTS = 9;

    private final Runnable onChange;
    private final NonNullList<ItemStack> filter = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
    private ItemStack lastVoided = ItemStack.EMPTY;
    private boolean allowList = true;
    private long voided;

    public TrashInventory(Runnable onChange) {
        this.onChange = onChange;
    }

    public ItemStack lastVoided() {
        return lastVoided;
    }

    public long voidedCount() {
        return voided;
    }

    public boolean allowList() {
        return allowList;
    }

    public void setAllowList(boolean allowList) {
        if (this.allowList != allowList) {
            this.allowList = allowList;
            onChange.run();
        }
    }

    public void clearFilter() {
        filter.replaceAll(stack -> ItemStack.EMPTY);
        onChange.run();
    }

    public void voidItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        lastVoided = stack.copy();
        voided += stack.getCount();
        onChange.run();
    }

    /** Whether automation may void this stack: an empty filter accepts everything. */
    public boolean accepts(ItemStack stack) {
        boolean any = false;
        boolean listed = false;
        for (ItemStack entry : filter) {
            if (!entry.isEmpty()) {
                any = true;
                if (ItemStack.isSameItemSameComponents(entry, stack)) {
                    listed = true;
                    break;
                }
            }
        }
        return !any || listed == allowList;
    }

    // -- Views used by the menu -----------------------------------------------------------------------

    /** The input slot: anything placed in it is voided at once. */
    public final Container input = new SingleSlot() {
        @Override
        public ItemStack getItem(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            voidItem(stack);
        }
    };

    /** The "last voided" slot: take the item back out until something else is voided. */
    public final Container recovery = new SingleSlot() {
        @Override
        public ItemStack getItem(int slot) {
            return lastVoided;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack taken = lastVoided.split(amount);
            if (!taken.isEmpty()) {
                onChange.run();
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack taken = lastVoided;
            lastVoided = ItemStack.EMPTY;
            return taken;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            lastVoided = stack;
            onChange.run();
        }
    };

    /** Ghost filter entries (single items, never real stacks). */
    public final Container filterContainer = new Container() {
        @Override
        public int getContainerSize() {
            return FILTER_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return filter.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return filter.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            filter.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            onChange.run();
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            clearFilter();
        }
    };

    /** What automation sees: one always-empty slot that voids whatever the filter accepts. */
    public final SlotAccess slotAccess = new SlotAccess() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !accepts(stack)) {
                return stack;
            }
            if (!simulate) {
                voidItem(stack);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            insertItem(slot, stack, false);
        }
    };

    // -- Persistence ------------------------------------------------------------------------------------

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!lastVoided.isEmpty()) {
            tag.put("LastVoided", lastVoided.save(registries));
        }
        CompoundTag filterTag = new CompoundTag();
        ContainerHelper.saveAllItems(filterTag, filter, false, registries);
        tag.put("Filter", filterTag);
        tag.putBoolean("AllowList", allowList);
        tag.putLong("Voided", voided);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        lastVoided = tag.contains("LastVoided") ? ItemStack.parseOptional(registries, tag.getCompound("LastVoided")) : ItemStack.EMPTY;
        filter.replaceAll(stack -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("Filter"), filter, registries);
        allowList = !tag.contains("AllowList") || tag.getBoolean("AllowList");
        voided = tag.getLong("Voided");
    }

    private abstract class SingleSlot implements Container {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getItem(0).isEmpty();
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
        }
    }
}
