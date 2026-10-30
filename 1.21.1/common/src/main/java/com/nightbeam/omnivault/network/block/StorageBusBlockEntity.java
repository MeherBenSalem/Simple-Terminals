package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.item.UpgradeItem;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.NetworkNode;
import com.nightbeam.omnivault.network.api.StorageProvider;
import com.nightbeam.omnivault.network.menu.BusMenu;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import com.nightbeam.omnivault.storage.SlotAccessStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;
import java.util.function.ObjLongConsumer;

/**
 * Makes the inventory it faces part of the network: chests, barrels, drawers, other mods'
 * storage, even another network's Network Port. Filter, priority and access mode apply.
 */
public class StorageBusBlockEntity extends BusBlockEntity implements StorageProvider, DirectionalDeviceBlock.TargetWatcher {
    public enum Access {
        BOTH, INSERT_ONLY, EXTRACT_ONLY
    }

    private int priority;
    private Access access = Access.BOTH;

    public StorageBusBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.STORAGE_BUS_BE.get(), pos, state);
    }

    @Override
    public BusMenu.Kind kind() {
        return BusMenu.Kind.STORAGE;
    }

    @Override
    protected boolean acceptsUpgrade(UpgradeItem.Kind kind) {
        return kind == UpgradeItem.Kind.FUZZY;
    }

    public int priority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Math.max(-999, Math.min(999, priority));
        onConfigChanged();
    }

    public Access access() {
        return access;
    }

    public void cycleAccess() {
        access = Access.values()[(access.ordinal() + 1) % Access.values().length];
        onConfigChanged();
    }

    @Override
    protected void onConfigChanged() {
        super.onConfigChanged();
        StorageNetwork network = getNetwork();
        if (network != null) {
            network.invalidateStorages();
        }
    }

    @Override
    public void onNeighborChanged(BlockPos neighbor) {
        if (neighbor.equals(worldPosition.relative(facing()))) {
            StorageNetwork network = getNetwork();
            if (network != null) {
                network.invalidateStorages();
            }
        }
    }

    @Override
    public void collectStorages(Consumer<ItemStorage> out) {
        if (level == null) {
            return;
        }
        // Never loop a network back into itself through its own Network Port.
        if (level.getBlockEntity(worldPosition.relative(facing())) instanceof NetworkNode node && node.getNetwork() == getNetwork()) {
            return;
        }
        target().ifPresent(access -> out.accept(new Filtered(new SlotAccessStorage(access))));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("priority", priority);
        tag.putInt("access", access.ordinal());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        priority = tag.getInt("priority");
        access = Access.values()[Math.floorMod(tag.getInt("access"), Access.values().length)];
    }

    /** The target inventory with this bus's filter, priority and access mode applied. */
    private final class Filtered implements ItemStorage {
        private final ItemStorage inner;

        Filtered(ItemStorage inner) {
            this.inner = inner;
        }

        @Override
        public long insert(ItemKey key, long amount, boolean simulate) {
            return access != Access.EXTRACT_ONLY && passes(key) ? inner.insert(key, amount, simulate) : 0;
        }

        @Override
        public long extract(ItemKey key, long amount, boolean simulate) {
            return access != Access.INSERT_ONLY ? inner.extract(key, amount, simulate) : 0;
        }

        @Override
        public void forEach(ObjLongConsumer<ItemKey> consumer) {
            if (access != Access.INSERT_ONLY) {
                inner.forEach(consumer);
            }
        }

        @Override
        public long getCount(ItemKey key) {
            return access != Access.INSERT_ONLY ? inner.getCount(key) : 0;
        }

        @Override
        public boolean isPreferredFor(ItemKey key) {
            // A storage bus with an allow-list is the preferred home for the items it lists.
            return passes(key) && (!filterIsEmpty() && !denyList || inner.isPreferredFor(key));
        }

        @Override
        public int priority() {
            return priority;
        }
    }
}
