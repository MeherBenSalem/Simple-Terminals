package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.content.CoreContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(OmniVault.MOD_ID)
@PrefixGameTestTemplate(false)
public class CoreGameTests {
    @GameTest(template = "floor_9x9")
    public static void omniumBlockPlaces(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, CoreContent.OMNIUM_BLOCK.get());
        helper.assertBlockPresent(CoreContent.OMNIUM_BLOCK.get(), pos);
        helper.succeed();
    }
}
