package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.api.NetworkBlock;
import com.nightbeam.omnivault.platform.services.IPlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Buses, the Destructor and the Constructor: a small body on a neck that reaches the back face. They join the
 * network on every side but the front, so a cable can also meet one from the side, where the body does not reach
 * the block face. The {@code north..down} properties say on which sides something joins; the model draws a
 * connector there. They never hold for the front (no connection) or the back (the neck already reaches it).
 */
public class BusBlock extends DirectionalDeviceBlock {
    public static final Map<Direction, BooleanProperty> CONNECTORS = CableBlock.CONNECTIONS;

    public BusBlock(IPlatformHelper.BlockEntityFactory<?> factory, double[][] northBoxes, Properties properties) {
        super(factory, northBoxes, properties);
        BlockState state = defaultBlockState();
        for (BooleanProperty property : CONNECTORS.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONNECTORS.values().toArray(new BooleanProperty[0]));
    }

    /** {@code state} with its connectors matching what is next to it now. */
    public BlockState withConnectors(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            state = state.setValue(CONNECTORS.get(side), joinsOnSide(state, level, pos, side));
        }
        return state;
    }

    private boolean joinsOnSide(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (side.getAxis() == state.getValue(FACING).getAxis() || !canConnectNetwork(state, side)) {
            return false;
        }
        BlockState other = level.getBlockState(pos.relative(side));
        return other.getBlock() instanceof NetworkBlock neighbour
                && neighbour.canConnectNetwork(other, side.getOpposite())
                && NetworkBlock.colorsMatch(networkColor(state), neighbour.networkColor(other));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : withConnectors(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        return updated.is(this) ? updated.setValue(CONNECTORS.get(direction), joinsOnSide(updated, level, pos, direction)) : updated;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Placed without a placement context (moved by the Relocator, a structure, a command): look around once.
        if (!level.isClientSide && level.getBlockState(pos) == state) {
            BlockState connected = withConnectors(state, level, pos);
            if (connected != state) {
                level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = super.rotate(state, rotation);
        for (Direction side : Direction.values()) {
            rotated = rotated.setValue(CONNECTORS.get(rotation.rotate(side)), state.getValue(CONNECTORS.get(side)));
        }
        return rotated;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = super.mirror(state, mirror);
        for (Direction side : Direction.values()) {
            mirrored = mirrored.setValue(CONNECTORS.get(mirror.mirror(side)), state.getValue(CONNECTORS.get(side)));
        }
        return mirrored;
    }
}
