package com.nightbeam.omnivault.network.block;

import com.mojang.serialization.MapCodec;
import com.nightbeam.omnivault.platform.services.IPlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A network device that points at a block (buses, the signal emitter): {@code facing} is the
 * side its working face touches. It connects to the network on every other side.
 */
public class DirectionalDeviceBlock extends DeviceBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final IPlatformHelper.BlockEntityFactory<?> factory;
    private final Map<Direction, VoxelShape> shapes;
    private final double[][] northBoxes;

    /**
     * @param northBoxes the shape when facing north, as {x0, y0, z0, x1, y1, z1} boxes in pixels; z = 0 is the working face
     */
    public DirectionalDeviceBlock(IPlatformHelper.BlockEntityFactory<?> factory, double[][] northBoxes, Properties properties) {
        super(properties);
        this.factory = factory;
        this.northBoxes = northBoxes;
        this.shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : northBoxes) {
                shape = Shapes.or(shape, rotate(box, direction));
            }
            shapes.put(direction, shape.optimize());
        }
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static VoxelShape rotate(double[] b, Direction facing) {
        double[] p = transform(b[0], b[1], b[2], facing);
        double[] q = transform(b[3], b[4], b[5], facing);
        return Block.box(Math.min(p[0], q[0]), Math.min(p[1], q[1]), Math.min(p[2], q[2]),
                Math.max(p[0], q[0]), Math.max(p[1], q[1]), Math.max(p[2], q[2]));
    }

    /** Matches the blockstate model rotations (y for horizontal, x 270 for up, x 90 for down). */
    private static double[] transform(double x, double y, double z, Direction facing) {
        return switch (facing) {
            case NORTH -> new double[]{x, y, z};
            case EAST -> new double[]{16 - z, y, x};
            case SOUTH -> new double[]{16 - x, y, 16 - z};
            case WEST -> new double[]{z, y, 16 - x};
            case UP -> new double[]{x, 16 - z, y};
            case DOWN -> new double[]{x, z, 16 - y};
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new DirectionalDeviceBlock(factory, northBoxes, properties));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.create(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MenuDevice device) {
            device.openMenu(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MenuDevice device) {
            device.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.getBlockEntity(pos) instanceof TargetWatcher watcher) {
            watcher.onNeighborChanged(neighborPos);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Block entities that care when the block they face changes. */
    public interface TargetWatcher {
        void onNeighborChanged(BlockPos neighbor);
    }
}
