package com.nightbeam.omnivault.util;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.nightbeam.omnivault.OmniVault;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saved data that a 1.21.1 world may already hold. 1.21.1 kept each of our files in the overworld's
 * storage as data/omnivault_&lt;name&gt;.dat; 26.x keeps saved data under data/&lt;namespace&gt;/&lt;path&gt;.dat,
 * and the world upgrade only moves vanilla's own files. So while a world has no file of ours yet, the old
 * one is read instead (with vanilla's item fixes applied to the stacks in it) and saved in the new place on
 * the next save. The old file is left as it was.
 *
 * @param legacyName the 1.21.1 file name, without ".dat"
 * @param codec      reads the old file's "data": the fields are the same as 1.21.1's
 * @param stacks     finds the item stacks in the old data, so they can be brought up to date
 */
public record LegacySavedData<T extends SavedData>(SavedDataType<T> type, String legacyName, Codec<T> codec, StackPaths stacks) {
    /** The data version of 1.21.1, for a file that has none. */
    private static final int V1_21_1 = 3955;

    /** The world's data of this type: its own file, else the one 1.21.1 left, else new data. */
    public T get(MinecraftServer server) {
        return get(server, server.getDataStorage(), server.getWorldPath(LevelResource.DATA));
    }

    /** As {@link #get(MinecraftServer)}, for a storage that keeps its files in {@code dataFolder}. */
    public T get(MinecraftServer server, SavedDataStorage storage, Path dataFolder) {
        T data = storage.get(type);
        if (data != null) {
            return data;
        }
        // A file of ours that didn't load is the storage's business, not a reason to go back to 1.21.1's.
        Path legacy = dataFolder.resolve(legacyName + ".dat");
        if (!Files.exists(type.id().withSuffix(".dat").resolveAgainst(dataFolder)) && Files.isRegularFile(legacy)) {
            data = read(server, legacy);
            if (data != null) {
                // Marks it dirty: the next save writes it in the new place.
                storage.set(type, data);
                OmniVault.LOG.info("Brought {} over from 1.21.1 ({})", type.id(), legacy.getFileName());
                return data;
            }
        }
        return storage.computeIfAbsent(type);
    }

    /** Reads a 1.21.1 file; null when it can't be read at all. Entries that no longer load are dropped with an error. */
    @Nullable
    public T read(MinecraftServer server, Path file) {
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            CompoundTag data = tag.getCompoundOrEmpty("data");
            stacks.update(data, new StackFixer(server.getFixerUpper(), NbtUtils.getDataVersion(tag, V1_21_1)));
            return codec.parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), data)
                    .resultOrPartial(error -> OmniVault.LOG.error("Problem reading {}: {}", file, error))
                    .orElse(null);
        } catch (IOException e) {
            OmniVault.LOG.error("Could not read {}", file, e);
            return null;
        }
    }

    /** Where an old file keeps its item stacks; each is brought up to date in place. */
    @FunctionalInterface
    public interface StackPaths {
        StackPaths NONE = (data, fixer) -> {
        };

        void update(CompoundTag data, StackFixer fixer);
    }

    /** Applies vanilla's item stack fixes from the file's data version to the current one. */
    public record StackFixer(DataFixer fixer, int version) {
        public Tag stack(Tag stack) {
            return fixer.update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, stack), version,
                    SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
        }

        /** A list of stacks, as {@code ContainerHelper} saves them (each with its "Slot"). */
        public void stacks(ListTag stacks) {
            for (int i = 0; i < stacks.size(); i++) {
                stacks.set(i, stack(stacks.get(i)));
            }
        }

        /** The stack saved under {@code key}, if there is one. */
        public void stack(CompoundTag tag, String key) {
            Tag stack = tag.get(key);
            if (stack != null) {
                tag.put(key, stack(stack));
            }
        }
    }
}
