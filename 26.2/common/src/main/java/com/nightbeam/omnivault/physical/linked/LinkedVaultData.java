package com.nightbeam.omnivault.physical.linked;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.util.LegacySavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Every Linked Vault inventory in the world, stored with the server and keyed by frequency. */
public class LinkedVaultData extends SavedData {
    public static final int SIZE = 27;
    private static final Codec<LinkedVaultData> CODEC = Entry.CODEC.listOf().fieldOf("Inventories").codec()
            .xmap(LinkedVaultData::load, LinkedVaultData::entries);
    // Mod data has no data fixers (both loaders accept a null fixer type).
    private static final SavedDataType<LinkedVaultData> TYPE = new SavedDataType<>(OmniVault.id("linked_vaults"), LinkedVaultData::new, CODEC, null);
    /** 1.21.1's file, read while a world has none of ours yet. */
    public static final LegacySavedData<LinkedVaultData> LEGACY = new LegacySavedData<>(TYPE, "omnivault_linked_vaults", CODEC, (data, fixer) -> {
        for (Tag entry : data.getListOrEmpty("Inventories")) {
            if (entry instanceof CompoundTag inventory) {
                fixer.stacks(inventory.getListOrEmpty("Items"));
            }
        }
    });

    private final Map<String, Inventory> inventories = new HashMap<>();

    public static LinkedVaultData get(MinecraftServer server) {
        return LEGACY.get(server);
    }

    public Inventory inventory(LinkedFrequency frequency) {
        return inventories.computeIfAbsent(frequency.key(), key -> new Inventory(this));
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        inventories.forEach((key, inventory) -> {
            if (!inventory.isEmpty()) {
                List<ItemStackWithSlot> items = new ArrayList<>();
                for (int slot = 0; slot < SIZE; slot++) {
                    if (!inventory.getItem(slot).isEmpty()) {
                        items.add(new ItemStackWithSlot(slot, inventory.getItem(slot)));
                    }
                }
                entries.add(new Entry(key, items));
            }
        });
        return entries;
    }

    private static LinkedVaultData load(List<Entry> entries) {
        LinkedVaultData data = new LinkedVaultData();
        for (Entry entry : entries) {
            Inventory inventory = new Inventory(data);
            for (ItemStackWithSlot item : entry.items()) {
                if (item.isValidInContainer(SIZE)) {
                    inventory.getItems().set(item.slot(), item.stack());
                }
            }
            data.inventories.put(entry.key(), inventory);
        }
        return data;
    }

    /** One frequency's inventory, saved as vanilla containers save their items. */
    private record Entry(String key, List<ItemStackWithSlot> items) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("Key").forGetter(Entry::key),
                ItemStackWithSlot.CODEC.listOf().optionalFieldOf("Items", List.of()).forGetter(Entry::items)
        ).apply(i, Entry::new));
    }

    /** A shared 27-slot inventory; any change marks the saved data dirty and bumps {@link #version()}. */
    public static final class Inventory extends SimpleContainer {
        private final LinkedVaultData owner;
        private long version;

        private Inventory(LinkedVaultData owner) {
            super(SIZE);
            this.owner = owner;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            version++;
            owner.setDirty();
        }

        public long version() {
            return version;
        }
    }
}
