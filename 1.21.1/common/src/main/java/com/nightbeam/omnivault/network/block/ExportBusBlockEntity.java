package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.TickingNode;
import com.nightbeam.omnivault.network.menu.BusMenu;
import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.storage.FluidStorage;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import com.nightbeam.omnivault.storage.SlotAccess;
import com.nightbeam.omnivault.storage.SlotAccessStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Pushes the items listed in its filter from the network into the inventory it faces. */
public class ExportBusBlockEntity extends BusBlockEntity implements TickingNode {
    private int nextFilterSlot;

    public ExportBusBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.EXPORT_BUS_BE.get(), pos, state);
    }

    @Override
    public BusMenu.Kind kind() {
        return BusMenu.Kind.EXPORT;
    }

    @Override
    public void networkTick(StorageNetwork network, long gameTime) {
        if (gameTime % interval() != 0 || !redstoneAllows() || filter.isEmpty()) {
            return;
        }
        exportFluids(network);
        SlotAccess target = target().orElse(null);
        if (target == null) {
            return;
        }
        ItemStorage storage = network.storage();
        SlotAccessStorage destination = new SlotAccessStorage(target);
        List<ItemKey> wanted = wantedKeys(storage);
        int budget = itemsPerOperation();
        // Round-robin over the filter so one item cannot starve the others.
        for (int i = 0; i < wanted.size() && budget > 0; i++) {
            ItemKey key = wanted.get((nextFilterSlot + i) % wanted.size());
            long available = storage.extract(key, budget, true);
            if (available <= 0) {
                continue;
            }
            long fits = destination.insert(key, available, true);
            if (fits <= 0) {
                continue;
            }
            long taken = storage.extract(key, fits, false);
            long placed = destination.insert(key, taken, false);
            if (placed < taken) {
                storage.insert(key, taken - placed, false);
            }
            budget -= (int) placed;
        }
        nextFilterSlot = wanted.isEmpty() ? 0 : (nextFilterSlot + 1) % wanted.size();
    }

    /** Fills the tanks it faces with the fluids whose filled buckets are in the filter. */
    private void exportFluids(StorageNetwork network) {
        FluidAccess tanks = fluidTarget().orElse(null);
        if (tanks == null) {
            return;
        }
        FluidStorage storage = network.fluidStorage();
        long budget = itemsPerOperation() * 125L;
        for (int slot = 0; slot < FILTER_SLOTS && budget > 0; slot++) {
            FluidKey key = FluidKey.fromBucket(filter.getItem(slot).getItem());
            if (key == null) {
                continue;
            }
            long available = storage.extract(key, budget, true);
            long fits = tanks.fill(key, available, true);
            if (fits <= 0) {
                continue;
            }
            long taken = storage.extract(key, fits, false);
            long filled = tanks.fill(key, taken, false);
            if (filled < taken) {
                storage.insert(key, taken - filled, false);
            }
            budget -= filled;
        }
    }

    private List<ItemKey> wantedKeys(ItemStorage storage) {
        List<ItemKey> keys = new ArrayList<>();
        boolean fuzzy = upgradeCount(com.nightbeam.omnivault.item.UpgradeItem.Kind.FUZZY) > 0;
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            ItemStack entry = filter.getItem(slot);
            if (entry.isEmpty()) {
                continue;
            }
            if (fuzzy) {
                // Fuzzy export sends any variant of the listed item (any damage, enchantments, data).
                storage.forEach((key, count) -> {
                    if (key.item() == entry.getItem() && !keys.contains(key)) {
                        keys.add(key);
                    }
                });
            } else {
                ItemKey key = ItemKey.of(entry);
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }
}
