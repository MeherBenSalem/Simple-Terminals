package com.nightbeam.omnivault.backpack;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nightbeam.omnivault.OmniVault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** A backpack set down in the world: opens on right-click, sneak-right-click with an empty hand picks it up. */
public class BackpackBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<BackpackBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BackpackTier.CODEC.fieldOf("tier").forGetter(BackpackBlock::tier),
            propertiesCodec()
    ).apply(i, BackpackBlock::new));
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** Dynamic drop name used by the backpack loot tables: the stored backpack item. */
    public static final ResourceLocation DROP = OmniVault.id("backpack");

    /** The model's silhouette facing north, in pixels: body, front pocket, side pockets, straps, handle. */
    private static final double[][] BOXES = {
            {2.5, 0, 5, 13.5, 13.25, 12.5},
            {3.75, 1, 3.25, 12.25, 6.75, 5},
            {1.75, 1.5, 6.75, 14.25, 7.25, 11.25},
            {4, 1.5, 12.5, 12, 12.5, 13.5},
            {6, 13, 8, 10.25, 15.25, 9.5},
    };
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : BOXES) {
                shape = Shapes.or(shape, rotated(b, facing));
            }
            SHAPES.put(facing, shape.optimize());
        }
    }

    private final BackpackTier tier;

    public BackpackBlock(BackpackTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    private static VoxelShape rotated(double[] b, Direction facing) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
            case EAST -> Block.box(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
            case WEST -> Block.box(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
            default -> Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
        };
    }

    public BackpackTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BackpackBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BackpackContent.BLOCK_ENTITY.get(), BackpackBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof BackpackBlockEntity blockEntity) {
            ItemStack stored = stack.copyWithCount(1);
            if (!level.isClientSide) {
                Backpacks.ensureId(stored);
            }
            blockEntity.setBackpack(stored);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BackpackBlockEntity blockEntity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive() && player.getMainHandItem().isEmpty()) {
            // Sneak + empty hand: put the backpack straight back into the hand.
            ItemStack stack = blockEntity.dropStack();
            blockEntity.setBackpack(ItemStack.EMPTY);
            level.removeBlock(pos, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 0.8f, 1.0f);
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            blockEntity.data();
            Backpacks.open(serverPlayer, new BackpackSource.Placed(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BackpackBlockEntity blockEntity) {
            ItemStack drop = blockEntity.dropStack();
            params.withDynamicDrop(DROP, output -> output.accept(drop));
        }
        return super.getDrops(state, params);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Creative players get no loot, but a backpack must never be lost with its contents.
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof BackpackBlockEntity blockEntity
                && Backpacks.getId(blockEntity.getBackpack()) != null) {
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, blockEntity.dropStack());
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
            blockEntity.setBackpack(ItemStack.EMPTY);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(this);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BackpackBlockEntity blockEntity ? blockEntity.analogSignal() : 0;
    }
}
