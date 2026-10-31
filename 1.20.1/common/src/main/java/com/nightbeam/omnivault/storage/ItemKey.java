package com.nightbeam.omnivault.storage;

import com.nightbeam.omnivault.packet.PacketCodec;
import com.nightbeam.omnivault.packet.PacketCodecs;
import com.nightbeam.omnivault.util.ItemStacks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * An item type: an item plus its NBT, without a count. Safe to use as a map key.
 */
public final class ItemKey {
    public static final PacketCodec<ItemKey> STREAM_CODEC = PacketCodecs.ITEM_STACK.map(ItemKey::of, ItemKey::toStack);

    private final ItemStack stack;
    private final int hash;

    private ItemKey(ItemStack stack) {
        this.stack = stack;
        this.hash = ItemStacks.hashItemAndTag(stack);
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
        return ItemStack.isSameItemSameTags(stack, other);
    }

    public Tag save() {
        return stack.save(new CompoundTag());
    }

    @Nullable
    public static ItemKey load(Tag tag) {
        if (!(tag instanceof CompoundTag compound)) {
            return null;
        }
        return of(ItemStack.of(compound));
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ItemKey other && hash == other.hash && ItemStack.isSameItemSameTags(stack, other.stack));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "ItemKey[" + stack.getItem() + (stack.getTag() == null ? "" : " " + stack.getTag()) + "]";
    }
}
