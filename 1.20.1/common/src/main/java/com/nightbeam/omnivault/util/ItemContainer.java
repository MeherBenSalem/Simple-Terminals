package com.nightbeam.omnivault.util;

import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * A {@link SimpleContainer} whose item list is reachable, as {@code SimpleContainer#getItems()} is in later
 * versions: saving and loading read and fill the list directly, without {@code setChanged} side effects.
 * The list is the very array the container was built on ({@code SimpleContainer(ItemStack...)} wraps it).
 */
public class ItemContainer extends SimpleContainer {
    private final NonNullList<ItemStack> items;

    public ItemContainer(int size) {
        this(empty(size));
    }

    private ItemContainer(ItemStack[] backing) {
        super(backing);
        this.items = NonNullList.of(ItemStack.EMPTY, backing);
    }

    private static ItemStack[] empty(int size) {
        ItemStack[] stacks = new ItemStack[size];
        Arrays.fill(stacks, ItemStack.EMPTY);
        return stacks;
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }
}
