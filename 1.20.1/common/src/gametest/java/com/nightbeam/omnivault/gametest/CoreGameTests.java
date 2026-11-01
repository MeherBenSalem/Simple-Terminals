package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.content.CoreContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(OmniVault.MOD_ID)
@PrefixGameTestTemplate(false)
public class CoreGameTests {
    @GameTest(template = Templates.FLOOR)
    public static void omniumBlockPlaces(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, CoreContent.OMNIUM_BLOCK.get());
        helper.assertBlockPresent(CoreContent.OMNIUM_BLOCK.get(), pos);
        helper.succeed();
    }

    @GameTest(template = Templates.FLOOR)
    public static void legacySimpleTerminalsIdLoadsAsCraftingTerminal(GameTestHelper helper) {
        var legacy = new net.minecraft.resources.ResourceLocation(OmniVault.LEGACY_MOD_ID, "crafting_terminal");
        helper.assertTrue(TestPlatform.INSTANCE.legacyEntry(net.minecraft.core.registries.Registries.BLOCK, legacy)
                == com.nightbeam.omnivault.network.NetworkContent.CRAFTING_TERMINAL.get(), "old block id resolves to the new terminal");
        helper.assertTrue(TestPlatform.INSTANCE.legacyEntry(net.minecraft.core.registries.Registries.ITEM, legacy)
                == com.nightbeam.omnivault.network.NetworkContent.CRAFTING_TERMINAL.get().asItem(), "old item id resolves to the new terminal");
        helper.succeed();
    }
}
