package com.nightbeam.simpleterminals.registry;

import com.nightbeam.simpleterminals.block.CraftingTerminalBlock;
import com.nightbeam.simpleterminals.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
    public static final CraftingTerminalBlock CRAFTING_TERMINAL = new CraftingTerminalBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE).noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal")))
    );

    public static void init() {
    }
}
