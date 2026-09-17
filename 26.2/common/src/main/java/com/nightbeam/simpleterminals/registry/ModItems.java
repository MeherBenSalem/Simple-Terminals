package com.nightbeam.simpleterminals.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final BlockItem CRAFTING_TERMINAL = new BlockItem(
            ModBlocks.CRAFTING_TERMINAL,
            new Item.Properties()
    );

    public static void init() {
    }
}
