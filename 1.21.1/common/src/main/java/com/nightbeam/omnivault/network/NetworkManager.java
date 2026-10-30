package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.network.api.NetworkBlock;
import com.nightbeam.omnivault.network.api.NetworkNode;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import java.util.function.Consumer;

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
                walk(level, pos, true, visited -> {
                    BlockEntity blockEntity = level.getBlockEntity(visited);
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

    /**
     * Breadth-first walk over connected network blocks in loaded chunks.
     *
     * @param includeNeighbours also start from the six neighbours of {@code start}, connected or not
     */
    public static void walk(ServerLevel level, BlockPos start, boolean includeNeighbours, Consumer<BlockPos> visitor) {
        int limit = NetworkConfig.MAX_NODES.get();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (isNetworkBlock(level, start)) {
            queue.add(start);
            seen.add(start);
        }
        if (includeNeighbours) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = start.relative(direction);
                if (isNetworkBlock(level, neighbour) && seen.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        while (!queue.isEmpty() && seen.size() <= limit) {
            BlockPos pos = queue.poll();
            visitor.accept(pos);
            BlockState state = level.getBlockState(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.contains(next) && connected(level, pos, state, direction)) {
                    seen.add(next);
                    queue.add(next);
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
