package com.nightbeam.omnivault.backpack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every backpack's contents, saved with the overworld ({@code data/omnivault_backpacks.dat}). Backpack
 * items only carry a UUID, so their stacks stay small no matter how full they are.
 */
public final class BackpackStorage extends SavedData {
    public static final String NAME = "omnivault_backpacks";

    private final Map<UUID, BackpackData> backpacks = new HashMap<>();

    public static BackpackStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BackpackStorage::load, BackpackStorage::new, NAME);
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

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        backpacks.forEach((id, data) -> {
            CompoundTag entry = data.save();
            entry.putUUID("Id", id);
            list.add(entry);
        });
        tag.put("Backpacks", list);
        return tag;
    }

    public static BackpackStorage load(CompoundTag tag) {
        BackpackStorage storage = new BackpackStorage();
        for (Tag element : tag.getList("Backpacks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            if (!entry.hasUUID("Id")) {
                continue;
            }
            BackpackData data = new BackpackData(0, 0, storage::setDirty);
            data.load(entry);
            storage.backpacks.put(entry.getUUID("Id"), data);
        }
        return storage;
    }
}
