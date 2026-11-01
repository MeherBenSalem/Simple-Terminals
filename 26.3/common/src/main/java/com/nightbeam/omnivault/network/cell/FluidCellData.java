package com.nightbeam.omnivault.network.cell;

import com.mojang.serialization.Codec;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.util.LegacySavedData;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Contents of every fluid cell, keyed by the cell's UUID and saved with the server. */
public class FluidCellData extends SavedData {
    // Fluid ids to amounts in mB, per cell.
    private static final Codec<FluidCellData> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(Codec.STRING, Codec.LONG))
            .fieldOf("cells").codec().xmap(FluidCellData::load, FluidCellData::save);
    private static final SavedDataType<FluidCellData> TYPE = new SavedDataType<>(OmniVault.id("fluid_cells"), FluidCellData::new, CODEC, null);
    /** 1.21.1's file, read while a world has none of ours yet. */
    public static final LegacySavedData<FluidCellData> LEGACY = new LegacySavedData<>(TYPE, "omnivault_fluid_cells", CODEC,
            LegacySavedData.StackPaths.NONE);
    private final Map<UUID, Tank> cells = new HashMap<>();

    public static FluidCellData get(MinecraftServer server) {
        return LEGACY.get(server);
    }

    public Tank getOrCreate(UUID id) {
        return cells.computeIfAbsent(id, key -> new Tank(this));
    }

    public Tank getIfPresent(UUID id) {
        return cells.get(id);
    }

    public void remove(UUID id) {
        if (cells.remove(id) != null) {
            setDirty();
        }
    }

    private static FluidCellData load(Map<UUID, Map<String, Long>> all) {
        FluidCellData data = new FluidCellData();
        all.forEach((id, fluids) -> {
            Tank tank = data.getOrCreate(id);
            fluids.forEach((fluid, amount) -> {
                FluidKey key = FluidKey.load(fluid);
                if (key != null && amount > 0) {
                    tank.fluids.addTo(key, (long) amount);
                    tank.stored += amount;
                } else if (amount > 0) {
                    OmniVault.LOG.warn("Dropping {} mB of unknown fluid {} from fluid cell {}", amount, fluid, id);
                }
            });
        });
        return data;
    }

    private Map<UUID, Map<String, Long>> save() {
        Map<UUID, Map<String, Long>> all = new HashMap<>();
        cells.forEach((id, tank) -> {
            if (tank.fluids.isEmpty()) {
                return;
            }
            Map<String, Long> fluids = new HashMap<>();
            tank.fluids.object2LongEntrySet().forEach(entry -> fluids.put(entry.getKey().id().toString(), entry.getLongValue()));
            all.put(id, fluids);
        });
        return all;
    }

    public static final class Tank {
        private final FluidCellData owner;
        final Object2LongLinkedOpenHashMap<FluidKey> fluids = new Object2LongLinkedOpenHashMap<>();
        long stored;

        Tank(FluidCellData owner) {
            this.owner = owner;
        }

        public long stored() {
            return stored;
        }

        public int types() {
            return fluids.size();
        }

        public long get(FluidKey key) {
            return fluids.getLong(key);
        }

        public boolean contains(FluidKey key) {
            return fluids.containsKey(key);
        }

        void add(FluidKey key, long amount) {
            long now = fluids.addTo(key, amount) + amount;
            if (now <= 0) {
                fluids.removeLong(key);
            }
            stored += amount;
            owner.setDirty();
        }
    }
}
