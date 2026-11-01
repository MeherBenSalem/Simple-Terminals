package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkManager;
import com.nightbeam.omnivault.network.api.NetworkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Inventory Cable: a network cable that also adds every inventory it touches (chests, barrels,
 * drawers, other mods' storage) to the network, with no bus needed. Works on any network.
 */
public class InventoryCableBlock extends CableBlock {
    public InventoryCableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (level instanceof Level real && !(neighborState.getBlock() instanceof NetworkBlock)) {
            // An inventory appeared or disappeared next to the cable.
            NetworkManager.queueUpdate(real, pos);
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }
}
