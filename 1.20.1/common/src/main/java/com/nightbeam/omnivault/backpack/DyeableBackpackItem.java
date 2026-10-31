package com.nightbeam.omnivault.backpack;

import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The leather backpack. Minecraft 1.20.1 dyes (crafting with dyes) only items that implement
 * {@link DyeableLeatherItem}; the colour is kept in the stack's {@code display.color}, like leather armour.
 */
public class DyeableBackpackItem extends BackpackItem implements DyeableLeatherItem {
    /** The undyed colour, opaque ARGB. */
    public static final int LEATHER_COLOR = 0xFF000000 | DyeableLeatherItem.DEFAULT_LEATHER_COLOR;

    public DyeableBackpackItem(Block block, BackpackTier tier, Properties properties) {
        super(block, tier, properties);
    }

    /** The backpack's colour as opaque ARGB; {@link #LEATHER_COLOR} for an undyed (or non-leather) backpack. */
    public static int color(ItemStack stack) {
        return stack.getItem() instanceof DyeableLeatherItem dyeable && dyeable.hasCustomColor(stack)
                ? 0xFF000000 | dyeable.getColor(stack) : LEATHER_COLOR;
    }
}
