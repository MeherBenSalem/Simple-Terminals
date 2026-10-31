package com.nightbeam.omnivault.backpack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.util.LegacySavedData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every backpack's contents, saved with the server ({@code data/omnivault/backpacks.dat}). Backpack
 * items only carry a UUID, so their stacks stay small no matter how full they are.
 */
public final class BackpackStorage extends SavedData {
    public static final Codec<BackpackStorage> CODEC = Entry.CODEC.listOf().fieldOf("Backpacks").codec()
            .xmap(BackpackStorage::load, BackpackStorage::entries);
    // Mod data has no data fixers (both loaders accept a null fixer type).
    private static final SavedDataType<BackpackStorage> TYPE = new SavedDataType<>(OmniVault.id("backpacks"), BackpackStorage::new, CODEC, null);
    /** 1.21.1's file, read while a world has none of ours yet. */
    public static final LegacySavedData<BackpackStorage> LEGACY = new LegacySavedData<>(TYPE, "omnivault_backpacks", CODEC, (data, fixer) -> {
        for (Tag entry : data.getListOrEmpty("Backpacks")) {
            if (entry instanceof CompoundTag backpack) {
                fixer.stacks(backpack.getCompoundOrEmpty("Items").getListOrEmpty("Items"));
                fixer.stacks(backpack.getCompoundOrEmpty("Upgrades").getListOrEmpty("Items"));
            }
        }
    });

    private final Map<UUID, BackpackData> backpacks = new HashMap<>();

    public static BackpackStorage get(MinecraftServer server) {
        return LEGACY.get(server);
    }

    /** The data for {@code id}, created empty if this backpack has never been opened; grown to fit {@code tier}. */
    public BackpackData getOrCreate(UUID id, BackpackTier tier) {
        BackpackData data = backpacks.computeIfAbsent(id, key -> {
            setDirty();
            return new BackpackData(tier.slots(), tier.upgradeSlots(), this::setDirty);
        });
        data.ensureCapacity(tier);
        return data;
    }

    @Nullable
    public BackpackData get(UUID id) {
        return backpacks.get(id);
    }

    public int size() {
        return backpacks.size();
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>(backpacks.size());
        backpacks.forEach((id, data) -> entries.add(new Entry(id, data.items().save(), data.upgrades().save())));
        return entries;
    }

    private static BackpackStorage load(List<Entry> entries) {
        BackpackStorage storage = new BackpackStorage();
        for (Entry entry : entries) {
            BackpackData data = new BackpackData(0, 0, storage::setDirty);
            data.load(entry.items(), entry.upgrades());
            storage.backpacks.put(entry.id(), data);
        }
        return storage;
    }

    private record Entry(UUID id, BackpackInventory.Contents items, BackpackInventory.Contents upgrades) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("Id").forGetter(Entry::id),
                BackpackInventory.Contents.CODEC.optionalFieldOf("Items", BackpackInventory.Contents.EMPTY).forGetter(Entry::items),
                BackpackInventory.Contents.CODEC.optionalFieldOf("Upgrades", BackpackInventory.Contents.EMPTY).forGetter(Entry::upgrades)
        ).apply(i, Entry::new));
    }
}
