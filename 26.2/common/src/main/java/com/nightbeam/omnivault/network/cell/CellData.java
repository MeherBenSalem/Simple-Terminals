package com.nightbeam.omnivault.network.cell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.storage.ItemKey;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contents of every storage cell in the world, keyed by the cell's UUID and saved with the
 * server. Keeping contents off the item stack means huge cells never bloat packets or NBT.
 */
public class CellData extends SavedData {
    /** Stored items that no longer exist are dropped with a logged error when the save loads. */
    private static final Codec<CellData> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC.listOf())
            .fieldOf("cells").codec().xmap(CellData::fromEntries, CellData::toEntries);
    // Mod data has no data fixers (both loaders accept a null fixer type).
    private static final SavedDataType<CellData> TYPE = new SavedDataType<>(OmniVault.id("cells"), CellData::new, CODEC, null);
    private final Map<UUID, CellInventory> cells = new HashMap<>();

    public static CellData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
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

    private static CellData fromEntries(Map<UUID, List<Entry>> all) {
        CellData data = new CellData();
        all.forEach((id, entries) -> {
            CellInventory inventory = data.getOrCreate(id);
            for (Entry entry : entries) {
                if (entry.count() > 0) {
                    inventory.items.addTo(entry.item(), entry.count());
                    inventory.stored += entry.count();
                }
            }
        });
        return data;
    }

    private Map<UUID, List<Entry>> toEntries() {
        Map<UUID, List<Entry>> all = new HashMap<>();
        cells.forEach((id, inventory) -> {
            if (inventory.items.isEmpty()) {
                return;
            }
            List<Entry> entries = new ArrayList<>(inventory.items.size());
            inventory.items.object2LongEntrySet().forEach(entry -> entries.add(new Entry(entry.getKey(), entry.getLongValue())));
            all.put(id, entries);
        });
        return all;
    }

    private record Entry(ItemKey item, long count) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemKey.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.LONG.fieldOf("count").forGetter(Entry::count)
        ).apply(i, Entry::new));
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
