package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.network.api.NetworkBlock;
import com.nightbeam.omnivault.network.api.NetworkNode;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import com.nightbeam.omnivault.network.block.QuantumBridgeBlockEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Notices topology changes (blocks placed, broken, loaded or unloaded) and tells the affected
 * Nexus Cores to rebuild at the end of the tick.
 */
public final class NetworkManager {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PENDING = new HashMap<>();

    private NetworkManager() {
    }

    public static void init() {
        OmniVaultCommon.onServerTick(NetworkManager::processPending);
    }

    /** Call when a network block appears, disappears or changes its connections at {@code pos}. */
    public static void queueUpdate(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return;
        }
        synchronized (PENDING) {
            PENDING.computeIfAbsent(level.dimension(), key -> new LinkedHashSet<>()).add(pos.immutable());
        }
    }

    private static void processPending(MinecraftServer server) {
        Map<ResourceKey<Level>, Set<BlockPos>> work;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return;
            }
            work = new HashMap<>(PENDING);
            PENDING.clear();
        }
        for (Map.Entry<ResourceKey<Level>, Set<BlockPos>> entry : work.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            Set<StorageNetwork> dirty = new HashSet<>();
            for (BlockPos pos : entry.getValue()) {
                BlockEntity at = level.getBlockEntity(pos);
                if (at instanceof NetworkNode node && node.getNetwork() != null) {
                    dirty.add(node.getNetwork());
                }
                // Walk outwards from the change and from each neighbour (a removed block splits the graph).
                walk(level, pos, true, (visitedLevel, visited) -> {
                    BlockEntity blockEntity = visitedLevel.getBlockEntity(visited);
                    if (blockEntity instanceof NexusCoreBlockEntity core) {
                        dirty.add(core.network());
                    } else if (blockEntity instanceof NetworkNode node && node.getNetwork() != null) {
                        dirty.add(node.getNetwork());
                    }
                });
            }
            dirty.forEach(StorageNetwork::markDirty);
        }
    }

    /** Receives every network block a walk reaches, with the level it is in. */
    @FunctionalInterface
    public interface Visitor {
        void visit(ServerLevel level, BlockPos pos);
    }

    private record Spot(ServerLevel level, BlockPos pos) {
        GlobalPos key() {
            return GlobalPos.of(level.dimension(), pos);
        }
    }

    /**
     * Breadth-first walk over connected network blocks in loaded chunks. Entangled Quantum Bridges
     * carry the walk to their partner, even in another dimension.
     *
     * @param includeNeighbours also start from the six neighbours of {@code start}, connected or not
     */
    public static void walk(ServerLevel level, BlockPos start, boolean includeNeighbours, Visitor visitor) {
        int limit = NetworkConfig.MAX_NODES.get();
        Set<GlobalPos> seen = new HashSet<>();
        ArrayDeque<Spot> queue = new ArrayDeque<>();
        if (isNetworkBlock(level, start)) {
            Spot first = new Spot(level, start);
            queue.add(first);
            seen.add(first.key());
        }
        if (includeNeighbours) {
            for (Direction direction : Direction.values()) {
                Spot neighbour = new Spot(level, start.relative(direction));
                if (isNetworkBlock(level, neighbour.pos()) && seen.add(neighbour.key())) {
                    queue.add(neighbour);
                }
            }
        }
        while (!queue.isEmpty() && seen.size() <= limit) {
            Spot spot = queue.poll();
            visitor.visit(spot.level(), spot.pos());
            BlockState state = spot.level().getBlockState(spot.pos());
            for (Direction direction : Direction.values()) {
                Spot next = new Spot(spot.level(), spot.pos().relative(direction));
                if (!seen.contains(next.key()) && connected(spot.level(), spot.pos(), state, direction)) {
                    seen.add(next.key());
                    queue.add(next);
                }
            }
            if (spot.level().getBlockEntity(spot.pos()) instanceof QuantumBridgeBlockEntity bridge) {
                QuantumBridgeBlockEntity partner = bridge.linkedPartner();
                if (partner != null && partner.getLevel() instanceof ServerLevel partnerLevel) {
                    Spot across = new Spot(partnerLevel, partner.getBlockPos());
                    if (seen.add(across.key())) {
                        queue.add(across);
                    }
                }
            }
        }
    }

    private static boolean isNetworkBlock(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof NetworkBlock;
    }

    /** Whether the network passes from {@code pos} (with {@code state}) to its neighbour in {@code direction}. */
    public static boolean connected(Level level, BlockPos pos, BlockState state, Direction direction) {
        if (!(state.getBlock() instanceof NetworkBlock self) || !self.canConnectNetwork(state, direction)) {
            return false;
        }
        BlockPos next = pos.relative(direction);
        if (!level.isLoaded(next)) {
            return false;
        }
        BlockState other = level.getBlockState(next);
        return other.getBlock() instanceof NetworkBlock neighbour
                && neighbour.canConnectNetwork(other, direction.getOpposite())
                && NetworkBlock.colorsMatch(self.networkColor(state), neighbour.networkColor(other));
    }
}
