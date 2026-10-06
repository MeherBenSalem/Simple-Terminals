package com.nightbeam.simpleterminals.registry;

import com.nightbeam.simpleterminals.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final BlockItem CRAFTING_TERMINAL = new BlockItem(
            ModBlocks.CRAFTING_TERMINAL,
            new Item.Properties().useBlockDescriptionPrefix().setId(ResourceKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal")))
    );

    public static void init() {
    }
}
