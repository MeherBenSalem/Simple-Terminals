package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkManager;
import com.nightbeam.omnivault.network.api.NetworkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Nexus Cable. Connects to every network block next to it. Dye it to keep parallel networks
 * apart: cables of two different colours never connect (uncoloured cable connects to any).
 */
public class CableBlock extends Block implements NetworkBlock, SimpleWaterloggedBlock {
    public static final Map<Direction, BooleanProperty> CONNECTIONS = new EnumMap<>(Map.of(
            Direction.NORTH, BlockStateProperties.NORTH, Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH, Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP, Direction.DOWN, BlockStateProperties.DOWN));
    public static final EnumProperty<CableColor> COLOR = EnumProperty.create("color", CableColor.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(5.5, 5.5, 0, 10.5, 10.5, 5),
            Direction.SOUTH, Block.box(5.5, 5.5, 11, 10.5, 10.5, 16),
            Direction.WEST, Block.box(0, 5.5, 5.5, 5, 10.5, 10.5),
            Direction.EAST, Block.box(11, 5.5, 5.5, 16, 10.5, 10.5),
            Direction.DOWN, Block.box(5.5, 0, 5.5, 10.5, 5, 10.5),
            Direction.UP, Block.box(5.5, 11, 5.5, 10.5, 16, 10.5)));
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CORE;
            for (Direction direction : Direction.values()) {
                if ((mask & (1 << direction.ordinal())) != 0) {
                    shape = Shapes.or(shape, ARMS.get(direction));
                }
            }
            SHAPES[mask] = shape.optimize();
        }
    }

    public CableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(COLOR, CableColor.NONE).setValue(WATERLOGGED, false);
        for (BooleanProperty property : CONNECTIONS.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTIONS.values().toArray(new BooleanProperty[0])).add(COLOR, WATERLOGGED);
    }

    @Override
    public boolean canConnectNetwork(BlockState state, Direction side) {
        return true;
    }

    @Override
    public int networkColor(BlockState state) {
        CableColor color = state.getValue(COLOR);
        return color == CableColor.NONE ? -1 : color.ordinal();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(WATERLOGGED,
                context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
        return withConnections(state, context.getLevel(), context.getClickedPos());
    }

    private BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(direction), connectsTo(state, level, pos, direction));
        }
        return state;
    }

    private boolean connectsTo(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        BlockState other = level.getBlockState(pos.relative(direction));
        return other.getBlock() instanceof NetworkBlock neighbour
                && neighbour.canConnectNetwork(other, direction.getOpposite())
                && NetworkBlock.colorsMatch(networkColor(state), neighbour.networkColor(other));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state.setValue(CONNECTIONS.get(direction), connectsTo(state, level, pos, direction));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        CableColor target = null;
        if (stack.getItem() instanceof DyeItem dye) {
            target = CableColor.of(dye.getDyeColor());
        } else if (stack.is(Items.WATER_BUCKET) || stack.is(Items.SPONGE)) {
            target = CableColor.NONE;
        }
        if (target == null || state.getValue(COLOR) == target) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            BlockState recoloured = withConnections(state.setValue(COLOR, target), level, pos);
            level.setBlock(pos, recoloured, Block.UPDATE_ALL);
            // Neighbouring cables recompute their own arms through updateShape.
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (!player.getAbilities().instabuild && stack.getItem() instanceof DyeItem) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) || oldState.getValue(COLOR) != state.getValue(COLOR)) {
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(direction))) {
                mask |= 1 << direction.ordinal();
            }
        }
        return SHAPES[mask];
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(WATERLOGGED);
    }

    public enum CableColor implements StringRepresentable {
        NONE(0x38E1FF), WHITE(0xF9FFFE), ORANGE(0xF9801D), MAGENTA(0xC74EBD), LIGHT_BLUE(0x3AB3DA), YELLOW(0xFED83D),
        LIME(0x80C71F), PINK(0xF38BAA), GRAY(0x6B7078), LIGHT_GRAY(0x9D9D97), CYAN(0x169C9C), PURPLE(0x8932B8),
        BLUE(0x3C44AA), BROWN(0x835432), GREEN(0x5E7C16), RED(0xB02E26), BLACK(0x2B2B33);

        /** Glow colour used to tint the cable's light strip. */
        public final int rgb;

        CableColor(int rgb) {
            this.rgb = rgb;
        }

        public static CableColor of(DyeColor dye) {
            return values()[dye.ordinal() + 1];
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
