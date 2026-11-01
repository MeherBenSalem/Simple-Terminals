package com.nightbeam.omnivault.physical.linked;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.util.ItemContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Every Linked Vault inventory in the world, stored with the overworld and keyed by frequency. */
public class LinkedVaultData extends SavedData {
    public static final int SIZE = 27;
    private static final String NAME = OmniVault.MOD_ID + "_linked_vaults";

    private final Map<String, Inventory> inventories = new HashMap<>();

    public static LinkedVaultData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LinkedVaultData::load, LinkedVaultData::new, NAME);
    }

    public Inventory inventory(LinkedFrequency frequency) {
        return inventories.computeIfAbsent(frequency.key(), key -> new Inventory(this));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        inventories.forEach((key, inventory) -> {
            if (!inventory.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("Key", key);
                ContainerHelper.saveAllItems(entry, inventory.getItems());
                list.add(entry);
            }
        });
        tag.put("Inventories", list);
        return tag;
    }

    private static LinkedVaultData load(CompoundTag tag) {
        LinkedVaultData data = new LinkedVaultData();
        ListTag list = tag.getList("Inventories", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Inventory inventory = new Inventory(data);
            ContainerHelper.loadAllItems(entry, inventory.getItems());
            data.inventories.put(entry.getString("Key"), inventory);
        }
        return data;
    }

    /** A shared 27-slot inventory; any change marks the saved data dirty and bumps {@link #version()}. */
    public static final class Inventory extends ItemContainer {
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
