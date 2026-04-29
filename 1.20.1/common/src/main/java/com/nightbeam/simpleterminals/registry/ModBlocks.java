package com.nightbeam.simpleterminals.registry;

import com.nightbeam.simpleterminals.block.CraftingTerminalBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
    public static final CraftingTerminalBlock CRAFTING_TERMINAL = new CraftingTerminalBlock(
            BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE).noOcclusion()
    );

    public static void init() {
    }
}
