package com.nightbeam.omnivault.physical;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Items whose right-click on a block must win over the block's own interaction (an upgrade kit on a
 * chest should upgrade it, not open it). Our blocks honour this directly; loaders additionally skip the
 * block interaction for other blocks (on NeoForge via {@code PlayerInteractEvent.RightClickBlock}), so
 * the item's {@code useOn} runs. Without that hook, sneak-right-click still reaches the item.
 */
public interface BlockUseOverride {
    boolean overridesBlockUse(ItemStack stack, Level level, BlockPos pos, BlockState state, Player player);

    /** Convenience for blocks: does the held item want to bypass this block's interaction? */
    static boolean applies(ItemStack stack, Level level, BlockPos pos, BlockState state, Player player) {
        return stack.getItem() instanceof BlockUseOverride override && override.overridesBlockUse(stack, level, pos, state, player);
    }
}
