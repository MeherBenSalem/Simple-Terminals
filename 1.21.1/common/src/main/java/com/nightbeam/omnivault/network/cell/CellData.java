package com.nightbeam.omnivault.network.cell;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.storage.ItemKey;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Contents of every storage cell in the world, keyed by the cell's UUID and saved with the
 * overworld. Keeping contents off the item stack means huge cells never bloat packets or NBT.
 */
public class CellData extends SavedData {
    private static final String NAME = OmniVault.MOD_ID + "_cells";
    private final Map<UUID, CellInventory> cells = new HashMap<>();

    public static CellData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CellData::new, CellData::load, null), NAME);
    }

    public CellInventory getOrCreate(UUID id) {
        return cells.computeIfAbsent(id, key -> new CellInventory(this));
    }

    public CellInventory getIfPresent(UUID id) {
        return cells.get(id);
    }

    public void remove(UUID id) {
        if (cells.remove(id) != null) {
            setDirty();
        }
    }

    private static CellData load(CompoundTag tag, HolderLookup.Provider registries) {
        CellData data = new CellData();
        CompoundTag all = tag.getCompound("cells");
        for (String id : all.getAllKeys()) {
            CellInventory inventory = data.getOrCreate(UUID.fromString(id));
            ListTag entries = all.getList(id, Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                ItemKey key = ItemKey.load(registries, entry.get("item"));
                long count = entry.getLong("count");
                if (key != null && count > 0) {
                    inventory.items.addTo(key, count);
                    inventory.stored += count;
                } else if (count > 0) {
                    OmniVault.LOG.warn("Dropping {} unknown items from storage cell {}", count, id);
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        cells.forEach((id, inventory) -> {
            if (inventory.items.isEmpty()) {
                return;
            }
            ListTag entries = new ListTag();
            inventory.items.object2LongEntrySet().forEach(entry -> {
                CompoundTag saved = new CompoundTag();
                saved.put("item", entry.getKey().save(registries));
                saved.putLong("count", entry.getLongValue());
                entries.add(saved);
            });
            all.put(id.toString(), entries);
        });
        tag.put("cells", all);
        return tag;
    }

    /** One cell's contents. */
    public static final class CellInventory {
        private final CellData owner;
        final Object2LongLinkedOpenHashMap<ItemKey> items = new Object2LongLinkedOpenHashMap<>();
        long stored;
        long version = 1;

        CellInventory(CellData owner) {
            this.owner = owner;
        }

        public long stored() {
            return stored;
        }

        public int types() {
            return items.size();
        }

        public long get(ItemKey key) {
            return items.getLong(key);
        }

        public boolean contains(ItemKey key) {
            return items.containsKey(key);
        }

        void add(ItemKey key, long amount) {
            long now = items.addTo(key, amount) + amount;
            if (now <= 0) {
                items.removeLong(key);
            }
            stored += amount;
            version++;
            owner.setDirty();
        }
    }
}
