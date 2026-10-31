package com.nightbeam.omnivault.registry;

import com.mojang.serialization.Codec;
import com.nightbeam.omnivault.OmniVault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Typed data on an item stack. Minecraft 1.20.1 keeps item data in the stack's NBT, so each component
 * is one entry of that tag, named after the component's id ({@code omnivault:cell_id}) and written with
 * its codec. Values are treated as immutable: {@link #set} replaces the entry.
 */
public final class ItemComponent<T> {
    private final String key;
    private final Codec<T> codec;

    ItemComponent(String key, Codec<T> codec) {
        this.key = key;
        this.codec = codec;
    }

    /** The entry's name in the stack's tag. */
    public String key() {
        return key;
    }

    @Nullable
    public T get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : read(tag);
    }

    public T getOrDefault(ItemStack stack, T fallback) {
        T value = get(stack);
        return value == null ? fallback : value;
    }

    public boolean has(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(key);
    }

    /** Sets the value; {@code null} removes it. */
    public void set(ItemStack stack, @Nullable T value) {
        if (value == null) {
            remove(stack);
            return;
        }
        write(stack.getOrCreateTag(), value);
    }

    public void remove(ItemStack stack) {
        if (stack.getTag() != null) {
            stack.removeTagKey(key);
        }
    }

    /** Copies this component, if present, from one stack to another. */
    public void copy(ItemStack from, ItemStack to) {
        CompoundTag tag = from.getTag();
        if (tag != null && tag.contains(key)) {
            to.getOrCreateTag().put(key, tag.get(key).copy());
        }
    }

    @Nullable
    public T read(CompoundTag tag) {
        Tag entry = tag.get(key);
        if (entry == null) {
            return null;
        }
        return codec.parse(NbtOps.INSTANCE, entry)
                .resultOrPartial(error -> OmniVault.LOG.warn("Ignoring invalid item data {}: {}", key, error))
                .orElse(null);
    }

    public void write(CompoundTag tag, T value) {
        codec.encodeStart(NbtOps.INSTANCE, value)
                .resultOrPartial(error -> OmniVault.LOG.warn("Could not save item data {}: {}", key, error))
                .ifPresent(encoded -> tag.put(key, encoded));
    }
}
