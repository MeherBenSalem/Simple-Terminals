package com.nightbeam.omnivault.storage;

import net.minecraft.world.item.ItemStack;

import java.util.function.ObjLongConsumer;

/** Exposes any slotted inventory (a chest, a drawer, another mod's machine) as counted storage. */
public class SlotAccessStorage implements ItemStorage {
    private final SlotAccess access;

    public SlotAccessStorage(SlotAccess access) {
        this.access = access;
    }

    public SlotAccess access() {
        return access;
    }

    @Override
    public boolean joinsTransactions() {
        return access.joinsTransactions();
    }

    @Override
    public long insert(ItemKey key, long amount, boolean simulate) {
        long remaining = amount;
        int slots = access.getSlots();
        // First top up stacks of the same item, then use empty slots.
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = 0; slot < slots && remaining > 0; slot++) {
                ItemStack existing = access.getStackInSlot(slot);
                boolean wanted = pass == 0 ? key.matches(existing) : existing.isEmpty();
                if (!wanted) {
                    continue;
                }
                int attempt = (int) Math.min(remaining, Math.max(1, access.getSlotLimit(slot)));
                ItemStack remainder = access.insertItem(slot, key.toStack(attempt), simulate);
                remaining -= attempt - remainder.getCount();
            }
        }
        return amount - remaining;
    }

    @Override
    public long extract(ItemKey key, long amount, boolean simulate) {
        long remaining = amount;
        for (int slot = access.getSlots() - 1; slot >= 0 && remaining > 0; slot--) {
            ItemStack existing = access.getStackInSlot(slot);
            if (!key.matches(existing)) {
                continue;
            }
            remaining -= extractFromSlot(slot, existing, key, (int) Math.min(remaining, Integer.MAX_VALUE), simulate);
        }
        return amount - remaining;
    }

    /**
     * Most handlers give out at most one stack per call, even from a slot holding thousands (drawers,
     * barrels, deep storage). Keep asking the same slot until it has given what was wanted; a
     * simulation, which can't repeat, counts on the slot's reported contents instead.
     */
    private long extractFromSlot(int slot, ItemStack existing, ItemKey key, int request, boolean simulate) {
        ItemStack extracted = access.extractItem(slot, request, simulate);
        if (!key.matches(extracted)) {
            return 0;
        }
        long got = extracted.getCount();
        if (got >= request || got < extracted.getMaxStackSize()) {
            return got;
        }
        if (simulate) {
            return Math.max(got, Math.min(request, existing.getCount()));
        }
        while (got < request) {
            ItemStack more = access.extractItem(slot, (int) (request - got), false);
            if (!key.matches(more)) {
                break;
            }
            got += more.getCount();
        }
        return got;
    }

    @Override
    public void forEach(ObjLongConsumer<ItemKey> consumer) {
        for (int slot = 0; slot < access.getSlots(); slot++) {
            ItemStack stack = access.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                consumer.accept(ItemKey.of(stack), stack.getCount());
            }
        }
    }

    @Override
    public long getCount(ItemKey key) {
        long total = 0;
        for (int slot = 0; slot < access.getSlots(); slot++) {
            ItemStack stack = access.getStackInSlot(slot);
            if (key.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
