package com.nightbeam.omnivault.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Blocks with their own reaction to the OmniTool. */
public interface Wrenchable {
    InteractionResult onWrench(Level level, BlockPos pos, BlockState state, Player player, Direction clickedFace);
}
