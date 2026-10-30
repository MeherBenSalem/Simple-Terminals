package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.TickingNode;
import com.nightbeam.omnivault.network.menu.BusMenu;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Pulls items that pass its filter out of the inventory it faces into the network. */
public class ImportBusBlockEntity extends BusBlockEntity implements TickingNode {
    public ImportBusBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.IMPORT_BUS_BE.get(), pos, state);
    }

    @Override
    public BusMenu.Kind kind() {
        return BusMenu.Kind.IMPORT;
    }

    @Override
    public void networkTick(StorageNetwork network, long gameTime) {
        if (gameTime % interval() != 0 || !redstoneAllows()) {
            return;
        }
        SlotAccess target = target().orElse(null);
        if (target == null) {
            return;
        }
        ItemStorage storage = network.storage();
        int budget = itemsPerOperation();
        for (int slot = 0; slot < target.getSlots() && budget > 0; slot++) {
            ItemStack inSlot = target.getStackInSlot(slot);
            ItemKey key = ItemKey.of(inSlot);
            if (key == null || !passes(key)) {
                continue;
            }
            ItemStack available = target.extractItem(slot, Math.min(budget, inSlot.getCount()), true);
            if (available.isEmpty()) {
                continue;
            }
            long accepted = storage.insert(key, available.getCount(), true);
            if (accepted <= 0) {
                continue;
            }
            ItemStack extracted = target.extractItem(slot, (int) accepted, false);
            long inserted = storage.insert(key, extracted.getCount(), false);
            if (inserted < extracted.getCount()) {
                // The network changed between simulation and insertion; put the rest back.
                target.insertItem(slot, extracted.copyWithCount((int) (extracted.getCount() - inserted)), false);
            }
            budget -= (int) inserted;
        }
    }
}
