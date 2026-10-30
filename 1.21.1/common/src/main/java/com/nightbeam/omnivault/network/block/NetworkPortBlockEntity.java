package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Exposes the whole network as an ordinary inventory: hoppers, pipes and machines from any mod
 * can insert into it, and pull from it, one slot per stored item type plus one empty slot.
 */
public class NetworkPortBlockEntity extends NetworkNodeBlockEntity implements ModCapabilities.ItemProvider {
    private final NetworkSlots slots = new NetworkSlots();

    public NetworkPortBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.NETWORK_PORT_BE.get(), pos, state);
    }

    @Override
    public int energyUsage() {
        return 2;
    }

    @Nullable
    @Override
    public SlotAccess getSlotAccess(@Nullable Direction side) {
        return slots;
    }

    private final class NetworkSlots implements SlotAccess {
        private List<ItemKey> keys = List.of();
        private long seenCounter = Long.MIN_VALUE;
        private int depth;

        @Nullable
        private StorageNetwork network() {
            StorageNetwork network = getNetwork();
            return network != null && network.isActive() ? network : null;
        }

        private List<ItemKey> keys() {
            StorageNetwork network = network();
            if (network == null || depth > 0) {
                return List.of();
            }
            if (network.changeCounter() != seenCounter) {
                depth++;
                try {
                    List<ItemKey> fresh = new ArrayList<>();
                    network.storage().forEach((key, count) -> fresh.add(key));
                    keys = fresh;
                    seenCounter = network.changeCounter();
                } finally {
                    depth--;
                }
            }
            return keys;
        }

        @Override
        public int getSlots() {
            return keys().size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            List<ItemKey> current = keys();
            StorageNetwork network = network();
            if (network == null || slot < 0 || slot >= current.size() || depth > 0) {
                return ItemStack.EMPTY;
            }
            ItemKey key = current.get(slot);
            depth++;
            try {
                long count = network.storage().getCount(key);
                return count <= 0 ? ItemStack.EMPTY : key.toStack((int) Math.min(count, key.maxStackSize()));
            } finally {
                depth--;
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            StorageNetwork network = network();
            ItemKey key = ItemKey.of(stack);
            // The depth guard stops two networks that see each other through ports from recursing forever.
            if (network == null || key == null || depth > 0) {
                return stack;
            }
            depth++;
            try {
                long inserted = network.storage().insert(key, stack.getCount(), simulate);
                return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount((int) (stack.getCount() - inserted));
            } finally {
                depth--;
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            List<ItemKey> current = keys();
            StorageNetwork network = network();
            if (network == null || slot < 0 || slot >= current.size() || amount <= 0 || depth > 0) {
                return ItemStack.EMPTY;
            }
            ItemKey key = current.get(slot);
            depth++;
            try {
                long extracted = network.storage().extract(key, Math.min(amount, key.maxStackSize()), simulate);
                return extracted <= 0 ? ItemStack.EMPTY : key.toStack((int) extracted);
            } finally {
                depth--;
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
    }
}
