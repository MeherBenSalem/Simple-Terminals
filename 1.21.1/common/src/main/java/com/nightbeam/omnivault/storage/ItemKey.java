package com.nightbeam.omnivault.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * An item type: an item plus its data components, without a count. Safe to use as a map key.
 */
public final class ItemKey {
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemKey> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(ItemKey::of, ItemKey::toStack);

    private final ItemStack stack;
    private final int hash;

    private ItemKey(ItemStack stack) {
        this.stack = stack;
        this.hash = ItemStack.hashItemAndComponents(stack);
    }

    /** @return the key for {@code stack}, or {@code null} for an empty stack. */
    @Nullable
    public static ItemKey of(ItemStack stack) {
        return stack.isEmpty() ? null : new ItemKey(stack.copyWithCount(1));
    }

    public Item item() {
        return stack.getItem();
    }

    /** A read-only view of this key as a single item. Never modify the returned stack. */
    public ItemStack view() {
        return stack;
    }

    public ItemStack toStack() {
        return stack.copy();
    }

    public ItemStack toStack(int count) {
        return stack.copyWithCount(count);
    }

    public int maxStackSize() {
        return stack.getMaxStackSize();
    }

    public boolean matches(ItemStack other) {
        return ItemStack.isSameItemSameComponents(stack, other);
    }

    public Tag save(HolderLookup.Provider registries) {
        return stack.save(registries);
    }

    @Nullable
    public static ItemKey load(HolderLookup.Provider registries, Tag tag) {
        if (!(tag instanceof CompoundTag)) {
            return null;
        }
        return ItemStack.parse(registries, tag).map(ItemKey::of).orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ItemKey other && hash == other.hash && ItemStack.isSameItemSameComponents(stack, other.stack));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "ItemKey[" + stack.getItem() + (stack.getComponentsPatch().isEmpty() ? "" : " " + stack.getComponentsPatch()) + "]";
    }
}
