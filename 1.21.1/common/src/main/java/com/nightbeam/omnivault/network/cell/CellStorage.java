package com.nightbeam.omnivault.network.cell;

import com.nightbeam.omnivault.network.NetworkConfig;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.function.ObjLongConsumer;

/** A storage cell inside a drive, as the network sees it. */
public class CellStorage implements ItemStorage {
    private final CellData.CellInventory inventory;
    private final CellTier tier;
    private final int priority;
    private final Runnable onChange;

    public CellStorage(CellData.CellInventory inventory, CellTier tier, int priority, Runnable onChange) {
        this.inventory = inventory;
        this.tier = tier;
        this.priority = priority;
        this.onChange = onChange;
    }

    public CellData.CellInventory inventory() {
        return inventory;
    }

    public CellTier tier() {
        return tier;
    }

    @Override
    public long insert(ItemKey key, long amount, boolean simulate) {
        if (!inventory.contains(key) && inventory.types() >= typeLimit()) {
            return 0;
        }
        long accepted = Math.min(amount, tier.capacity - inventory.stored());
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            inventory.add(key, accepted);
            onChange.run();
        }
        return accepted;
    }

    @Override
    public long extract(ItemKey key, long amount, boolean simulate) {
        long taken = Math.min(amount, inventory.get(key));
        if (taken > 0 && !simulate) {
            inventory.add(key, -taken);
            onChange.run();
        }
        return taken;
    }

    @Override
    public void forEach(ObjLongConsumer<ItemKey> consumer) {
        for (Object2LongMap.Entry<ItemKey> entry : inventory.items.object2LongEntrySet()) {
            consumer.accept(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public long getCount(ItemKey key) {
        return inventory.get(key);
    }

    @Override
    public boolean isPreferredFor(ItemKey key) {
        return inventory.contains(key);
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public long version() {
        return inventory.version;
    }

    public int typeLimit() {
        return tier.isCreative() ? Integer.MAX_VALUE : NetworkConfig.CELL_TYPES.get();
    }
}
