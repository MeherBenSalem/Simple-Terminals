package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.network.api.CraftingHooks;
import com.nightbeam.omnivault.network.api.NetworkNode;
import com.nightbeam.omnivault.network.api.StorageProvider;
import com.nightbeam.omnivault.network.api.TickingNode;
import com.nightbeam.omnivault.network.api.NetworkBlock;
import com.nightbeam.omnivault.network.block.DriveBlockEntity;
import com.nightbeam.omnivault.network.block.InventoryCableBlock;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.FluidAccessStorage;
import com.nightbeam.omnivault.storage.SlotAccessStorage;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import com.nightbeam.omnivault.network.block.WirelessTransmitterBlockEntity;
import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.storage.FluidStorage;
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
    private final NetworkFluidStorage fluidStorage = new NetworkFluidStorage();
    private final Set<NetworkNode> nodes = new LinkedHashSet<>();
    private final List<TickingNode> tickingNodes = new ArrayList<>();
    private final List<WirelessTransmitterBlockEntity> transmitters = new ArrayList<>();
    private final Map<String, Integer> deviceCounts = new HashMap<>();
    /** Inventory Cables (and a Storage Hub) whose neighbouring inventories join the network. */
    private final List<Anchor> inventoryAnchors = new ArrayList<>();

    private record Anchor(ServerLevel level, BlockPos pos) {
    }
    private final Map<Object, Object> attachments = new HashMap<>();
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

    /** True for an early-game Storage Hub network: plain inventories only. */
    public boolean isBasic() {
        return core.isBasic();
    }

    /** Autocrafting and wireless need a Nexus Core. */
    public boolean supportsAdvancedFeatures() {
        return !core.isBasic();
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

    /** The network's combined fluid storage (millibuckets), or an empty view while offline. */
    public FluidStorage fluidStorage() {
        return isActive() ? fluidStorage : EmptyFluids.INSTANCE;
    }

    public long fluidChangeCounter() {
        return fluidStorage.changeCounter() + (isActive() ? 0 : 1_000_000_000L);
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

    /** Per-network state owned by a feature (for example the autocrafting scheduler); lives as long as this network. */
    @SuppressWarnings("unchecked")
    public <T> T attachment(Object key, java.util.function.Function<StorageNetwork, T> factory) {
        return (T) attachments.computeIfAbsent(key, k -> factory.apply(this));
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
        double coreRange = NetworkConfig.CORE_RANGE.get();
        if (player.level() == level && player.distanceToSqr(core.getBlockPos().getCenter()) <= coreRange * coreRange) {
            return true;
        }
        // Transmitters reached through a Quantum Bridge cover their own dimension.
        for (WirelessTransmitterBlockEntity transmitter : transmitters) {
            double range = transmitter.range();
            if (!transmitter.isRemoved() && transmitter.getLevel() == player.level()
                    && player.distanceToSqr(transmitter.getBlockPos().getCenter()) <= range * range) {
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
        if (gameTime % 20 == 0) {
            fluidStorage.poll();
        }
        for (TickingNode node : tickingNodes) {
            if (!(node instanceof BlockEntity be) || !be.isRemoved()) {
                node.networkTick(this, gameTime);
            }
        }
        if (supportsAdvancedFeatures()) {
            CraftingHooks.get().tick(this, gameTime);
        }
    }

    private void rebuild() {
        topologyDirty = false;
        Set<NetworkNode> found = new LinkedHashSet<>();
        boolean[] otherCore = {false};
        inventoryAnchors.clear();
        if (core.isBasic()) {
            inventoryAnchors.add(new Anchor(level, core.getBlockPos()));
        }
        NetworkManager.walk(level, core.getBlockPos(), false, (at, pos) -> {
            if (at.getBlockState(pos).getBlock() instanceof InventoryCableBlock) {
                inventoryAnchors.add(new Anchor(at, pos));
            }
            BlockEntity blockEntity = at.getBlockEntity(pos);
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
        List<FluidStorage> fluids = new ArrayList<>();
        long total = 0;
        collectTouchingInventories(collected, fluids);
        for (NetworkNode node : nodes) {
            // Storage Hub networks hold plain inventories only; cells need a Nexus Core.
            if (core.isBasic() && node instanceof DriveBlockEntity) {
                continue;
            }
            if (node instanceof StorageProvider provider) {
                provider.collectStorages(collected::add);
                provider.collectFluidStorages(fluids::add);
                long cap = provider.capacity();
                if (cap > 0) {
                    total += cap;
                }
            }
        }
        capacity = total;
        storage.setStorages(collected);
        fluidStorage.setStorages(fluids);
    }

    /** Every inventory touching an Inventory Cable or the Storage Hub, each counted once. */
    private void collectTouchingInventories(List<ItemStorage> items, List<FluidStorage> fluids) {
        Set<net.minecraft.core.GlobalPos> seen = new java.util.HashSet<>();
        for (Anchor anchor : inventoryAnchors) {
            ServerLevel level = anchor.level();
            for (Direction direction : Direction.values()) {
                BlockPos target = anchor.pos().relative(direction);
                if (!level.isLoaded(target) || level.getBlockState(target).getBlock() instanceof NetworkBlock
                        || !seen.add(net.minecraft.core.GlobalPos.of(level.dimension(), target))) {
                    continue;
                }
                // A double chest answers for both halves; skip the other half.
                BlockState state = level.getBlockState(target);
                if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                    BlockPos other = target.relative(ChestBlock.getConnectedDirection(state));
                    if (!seen.add(net.minecraft.core.GlobalPos.of(level.dimension(), other))) {
                        continue;
                    }
                }
                Services.PLATFORM.findSlotAccess(level, target, direction.getOpposite())
                        .ifPresent(access -> items.add(new SlotAccessStorage(access)));
                Services.PLATFORM.findFluidAccess(level, target, direction.getOpposite())
                        .ifPresent(access -> fluids.add(new FluidAccessStorage(access)));
            }
        }
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

    private enum EmptyFluids implements FluidStorage {
        INSTANCE;

        @Override
        public long insert(FluidKey key, long amount, boolean simulate) {
            return 0;
        }

        @Override
        public long extract(FluidKey key, long amount, boolean simulate) {
            return 0;
        }

        @Override
        public void forEach(java.util.function.ObjLongConsumer<FluidKey> consumer) {
        }
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
