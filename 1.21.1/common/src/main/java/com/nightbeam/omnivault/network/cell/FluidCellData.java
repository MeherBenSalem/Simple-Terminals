package com.nightbeam.omnivault.network.cell;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.storage.FluidKey;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Contents of every fluid cell, keyed by the cell's UUID and saved with the overworld. */
public class FluidCellData extends SavedData {
    private static final String NAME = OmniVault.MOD_ID + "_fluid_cells";
    private final Map<UUID, Tank> cells = new HashMap<>();

    public static FluidCellData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FluidCellData::new, FluidCellData::load, null), NAME);
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

    private static FluidCellData load(CompoundTag tag, HolderLookup.Provider registries) {
        FluidCellData data = new FluidCellData();
        CompoundTag all = tag.getCompound("cells");
        for (String id : all.getAllKeys()) {
            Tank tank = data.getOrCreate(UUID.fromString(id));
            CompoundTag fluids = all.getCompound(id);
            for (String fluid : fluids.getAllKeys()) {
                FluidKey key = FluidKey.load(fluid);
                long amount = fluids.getLong(fluid);
                if (key != null && amount > 0) {
                    tank.fluids.addTo(key, amount);
                    tank.stored += amount;
                } else if (amount > 0) {
                    OmniVault.LOG.warn("Dropping {} mB of unknown fluid {} from fluid cell {}", amount, fluid, id);
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        cells.forEach((id, tank) -> {
            if (tank.fluids.isEmpty()) {
                return;
            }
            CompoundTag fluids = new CompoundTag();
            tank.fluids.object2LongEntrySet().forEach(entry -> fluids.putLong(entry.getKey().id().toString(), entry.getLongValue()));
            all.put(id.toString(), fluids);
        });
        tag.put("cells", all);
        return tag;
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
