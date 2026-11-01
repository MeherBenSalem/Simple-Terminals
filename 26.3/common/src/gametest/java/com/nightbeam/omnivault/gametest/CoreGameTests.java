package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.content.CoreContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public class CoreGameTests {
    @GameTest(structure = Templates.FLOOR, maxTicks = Templates.TICKS)
    public void omniumBlockPlaces(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, CoreContent.OMNIUM_BLOCK.get());
        helper.assertBlockPresent(CoreContent.OMNIUM_BLOCK.get(), pos);
        helper.succeed();
    }

    @GameTest(structure = Templates.FLOOR, maxTicks = Templates.TICKS)
    public void legacySimpleTerminalsIdLoadsAsCraftingTerminal(GameTestHelper helper) {
        var legacy = net.minecraft.resources.Identifier.fromNamespaceAndPath(OmniVault.LEGACY_MOD_ID, "crafting_terminal");
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(legacy)
                == com.nightbeam.omnivault.network.NetworkContent.CRAFTING_TERMINAL.get(), "old block id resolves to the new terminal");
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(legacy)
                == com.nightbeam.omnivault.network.NetworkContent.CRAFTING_TERMINAL.get().asItem(), "old item id resolves to the new terminal");
        helper.succeed();
    }
}
