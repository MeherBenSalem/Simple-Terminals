package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.network.api.CraftingHooks;
import com.nightbeam.omnivault.network.api.NetworkNode;
import com.nightbeam.omnivault.network.api.StorageProvider;
import com.nightbeam.omnivault.network.api.TickingNode;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import com.nightbeam.omnivault.network.block.WirelessTransmitterBlockEntity;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One storage network: everything connected to a single Nexus Core. There are no channel limits;
 * any number of devices can share a network.
 */
public class StorageNetwork {
    public enum State {
        ONLINE, NO_POWER, CONFLICT
    }

    private final ServerLevel level;
    private final NexusCoreBlockEntity core;
    private final NetworkStorage storage = new NetworkStorage();
    private final Set<NetworkNode> nodes = new LinkedHashSet<>();
    private final List<TickingNode> tickingNodes = new ArrayList<>();
    private final List<WirelessTransmitterBlockEntity> transmitters = new ArrayList<>();
    private final Map<String, Integer> deviceCounts = new HashMap<>();
    private boolean topologyDirty = true;
    private boolean storagesDirty = true;
    private boolean conflict;
    private boolean powered = true;
    private boolean removed;
    private State lastState;
    private int energyUsage;
    private long capacity;

    public StorageNetwork(ServerLevel level, NexusCoreBlockEntity core) {
        this.level = level;
        this.core = core;
    }

    public ServerLevel level() {
        return level;
    }

    public NexusCoreBlockEntity core() {
        return core;
    }

    public BlockPos corePos() {
        return core.getBlockPos();
    }

    public void markDirty() {
        topologyDirty = true;
    }

    /** A drive's cells or a storage bus's target changed. */
    public void invalidateStorages() {
        storagesDirty = true;
    }

    public boolean isRemoved() {
        return removed;
    }

    public State state() {
        if (conflict) {
            return State.CONFLICT;
        }
        return powered ? State.ONLINE : State.NO_POWER;
    }

    public boolean isActive() {
        return !removed && state() == State.ONLINE;
    }

    public void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            storage.invalidate();
        }
    }

    /** The network's combined storage, or an empty view while offline. */
    public ItemStorage storage() {
        return isActive() ? storage : EmptyStorage.INSTANCE;
    }

    public NetworkStorage rawStorage() {
        return storage;
    }

    public long changeCounter() {
        return storage.changeCounter() + (isActive() ? 0 : 1_000_000_000L);
    }

    public int energyUsage() {
        return energyUsage;
    }

    public long capacity() {
        return capacity;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public Map<String, Integer> deviceCounts() {
        return Collections.unmodifiableMap(deviceCounts);
    }

    public List<WirelessTransmitterBlockEntity> transmitters() {
        return transmitters;
    }

    public Set<NetworkNode> nodes() {
        return Collections.unmodifiableSet(nodes);
    }

    // -- convenience ------------------------------------------------------------------------------

    /** Inserts a stack and returns what did not fit. */
    public ItemStack insert(ItemStack stack) {
        ItemKey key = ItemKey.of(stack);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        long inserted = storage().insert(key, stack.getCount(), false);
        return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount((int) (stack.getCount() - inserted));
    }

    public ItemStack extract(ItemKey key, int amount) {
        long extracted = storage().extract(key, amount, false);
        return extracted <= 0 ? ItemStack.EMPTY : key.toStack((int) extracted);
    }

    /** Whether {@code player} may use this network wirelessly from where they stand. */
    public boolean inWirelessRange(Player player) {
        if (player.level() != level) {
            return false;
        }
        double coreRange = NetworkConfig.CORE_RANGE.get();
        if (player.distanceToSqr(core.getBlockPos().getCenter()) <= coreRange * coreRange) {
            return true;
        }
        for (WirelessTransmitterBlockEntity transmitter : transmitters) {
            double range = transmitter.range();
            if (!transmitter.isRemoved() && player.distanceToSqr(transmitter.getBlockPos().getCenter()) <= range * range) {
                return true;
            }
        }
        return false;
    }

    // -- lifecycle ---------------------------------------------------------------------------------

    public void tick(long gameTime) {
        if (removed) {
            return;
        }
        if (topologyDirty) {
            rebuild();
        }
        if (storagesDirty) {
            collectStorages();
        }
        State now = state();
        if (now != lastState) {
            lastState = now;
            for (NetworkNode node : List.copyOf(nodes)) {
                node.onNetworkStateChanged();
            }
        }
        if (!isActive()) {
            return;
        }
        storage.tick();
        for (TickingNode node : tickingNodes) {
            if (!(node instanceof BlockEntity be) || !be.isRemoved()) {
                node.networkTick(this, gameTime);
            }
        }
        CraftingHooks.get().tick(this, gameTime);
    }

    private void rebuild() {
        topologyDirty = false;
        Set<NetworkNode> found = new LinkedHashSet<>();
        boolean[] otherCore = {false};
        NetworkManager.walk(level, core.getBlockPos(), false, pos -> {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof NexusCoreBlockEntity other && other != core) {
                otherCore[0] = true;
            }
            if (blockEntity instanceof NetworkNode node) {
                found.add(node);
            }
        });
        boolean wasConflict = conflict;
        conflict = otherCore[0];
        for (NetworkNode old : nodes) {
            if (!found.contains(old) && old.getNetwork() == this) {
                old.setNetwork(null);
            }
        }
        nodes.clear();
        nodes.addAll(found);
        tickingNodes.clear();
        transmitters.clear();
        deviceCounts.clear();
        energyUsage = NetworkConfig.CORE_USAGE.get();
        for (NetworkNode node : nodes) {
            if (node.getNetwork() != this) {
                node.setNetwork(this);
            }
            if (node instanceof TickingNode ticking) {
                tickingNodes.add(ticking);
            }
            if (node instanceof WirelessTransmitterBlockEntity transmitter) {
                transmitters.add(transmitter);
            }
            if (node != core) {
                energyUsage += node.energyUsage();
            }
            if (node instanceof BlockEntity be) {
                deviceCounts.merge(be.getBlockState().getBlock().getDescriptionId(), 1, Integer::sum);
            }
        }
        if (wasConflict != conflict) {
            storage.invalidate();
        }
        storagesDirty = true;
    }

    private void collectStorages() {
        storagesDirty = false;
        List<ItemStorage> collected = new ArrayList<>();
        long total = 0;
        for (NetworkNode node : nodes) {
            if (node instanceof StorageProvider provider) {
                provider.collectStorages(collected::add);
                long cap = provider.capacity();
                if (cap > 0) {
                    total += cap;
                }
            }
        }
        capacity = total;
        storage.setStorages(collected);
    }

    /** Called when the core is removed or unloaded. */
    public void destroy() {
        removed = true;
        for (NetworkNode node : nodes) {
            if (node.getNetwork() == this) {
                node.setNetwork(null);
            }
        }
        nodes.clear();
        tickingNodes.clear();
        transmitters.clear();
    }

    private enum EmptyStorage implements ItemStorage {
        INSTANCE;

        @Override
        public long insert(ItemKey key, long amount, boolean simulate) {
            return 0;
        }

        @Override
        public long extract(ItemKey key, long amount, boolean simulate) {
            return 0;
        }

        @Override
        public void forEach(java.util.function.ObjLongConsumer<ItemKey> consumer) {
        }

        @Override
        public long getCount(ItemKey key) {
            return 0;
        }
    }
}
