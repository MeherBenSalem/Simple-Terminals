package com.nightbeam.omnivault.network.cell;

import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.storage.FluidStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.function.ObjLongConsumer;

public class FluidCellStorage implements FluidStorage {
    private final FluidCellData.Tank tank;
    private final FluidCellItem cell;
    private final int priority;
    private final Runnable onChange;

    public FluidCellStorage(FluidCellData.Tank tank, FluidCellItem cell, int priority, Runnable onChange) {
        this.tank = tank;
        this.cell = cell;
        this.priority = priority;
        this.onChange = onChange;
    }

    @Override
    public long insert(FluidKey key, long amount, boolean simulate) {
        if (!tank.contains(key) && tank.types() >= cell.typeLimit()) {
            return 0;
        }
        long accepted = Math.min(amount, cell.capacity() - tank.stored());
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            tank.add(key, accepted);
            onChange.run();
        }
        return accepted;
    }

    @Override
    public long extract(FluidKey key, long amount, boolean simulate) {
        long taken = Math.min(amount, tank.get(key));
        if (taken > 0 && !simulate) {
            tank.add(key, -taken);
            onChange.run();
        }
        return taken;
    }

    @Override
    public void forEach(ObjLongConsumer<FluidKey> consumer) {
        for (Object2LongMap.Entry<FluidKey> entry : tank.fluids.object2LongEntrySet()) {
            consumer.accept(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public long getAmount(FluidKey key) {
        return tank.get(key);
    }

    @Override
    public boolean isPreferredFor(FluidKey key) {
        return tank.contains(key);
    }

    @Override
    public int priority() {
        return priority;
    }
}
