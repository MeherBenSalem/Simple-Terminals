package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkManager;
import com.nightbeam.omnivault.network.api.NetworkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (level instanceof Level real && !(neighborState.getBlock() instanceof NetworkBlock)) {
            // An inventory appeared or disappeared next to the cable.
            NetworkManager.queueUpdate(real, pos);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
}
