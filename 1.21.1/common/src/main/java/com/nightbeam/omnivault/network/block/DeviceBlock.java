package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkManager;
import com.nightbeam.omnivault.network.api.NetworkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Base block for network devices: tells the network manager whenever the block appears,
 * disappears or changes which sides it connects on.
 */
public abstract class DeviceBlock extends BaseEntityBlock implements NetworkBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    protected DeviceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canConnectNetwork(BlockState state, Direction side) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && !connectsThroughFront()) {
            return side != state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        if (state.hasProperty(BlockStateProperties.FACING) && !connectsThroughFront()) {
            return side != state.getValue(BlockStateProperties.FACING);
        }
        return true;
    }

    /** Devices whose front is a screen or a working face do not connect through it. */
    protected boolean connectsThroughFront() {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock()) || connectionsDiffer(oldState, state)) {
            NetworkManager.queueUpdate(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            NetworkManager.queueUpdate(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private boolean connectionsDiffer(BlockState a, BlockState b) {
        for (Direction direction : Direction.values()) {
            if (canConnectNetwork(a, direction) != canConnectNetwork(b, direction)) {
                return true;
            }
        }
        return false;
    }
}
