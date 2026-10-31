package com.nightbeam.omnivault.storage;

import com.mojang.serialization.Codec;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** An immutable copy of a small inventory, for item data such as a drawer's upgrades. */
public final class ItemList {
    public static final ItemList EMPTY = new ItemList(List.of());
    public static final Codec<ItemList> CODEC = ItemStack.CODEC.listOf().xmap(ItemList::new, list -> list.items);

    private final List<ItemStack> items;

    private ItemList(List<ItemStack> items) {
        List<ItemStack> copy = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            copy.add(stack.copy());
        }
        this.items = List.copyOf(copy);
    }

    public static ItemList fromItems(List<ItemStack> items) {
        return new ItemList(items);
    }

    /** Copies the stacks into {@code target}, slot by slot, as far as both reach. */
    public void copyInto(NonNullList<ItemStack> target) {
        for (int slot = 0; slot < target.size(); slot++) {
            target.set(slot, slot < items.size() ? items.get(slot).copy() : ItemStack.EMPTY);
        }
    }

    public Stream<ItemStack> stream() {
        return items.stream().map(ItemStack::copy);
    }

    public Iterable<ItemStack> nonEmptyItems() {
        return items.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }
}
