package com.nightbeam.omnivault.physical.linked;

import com.mojang.serialization.MapCodec;
import com.nightbeam.omnivault.content.BlockUseOverride;
import com.nightbeam.omnivault.content.Wrenchable;
import com.nightbeam.omnivault.physical.PhysicalConfig;
import com.nightbeam.omnivault.physical.PhysicalContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ender-Storage-style vault. Right-click one of the three lid latches with a dye to set the frequency;
 * use a diamond or the OmniTool to toggle private (owner-only) mode.
 */
public class LinkedVaultBlock extends BaseEntityBlock implements SimpleWaterloggedBlock, Wrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final MapCodec<LinkedVaultBlock> CODEC = simpleCodec(LinkedVaultBlock::new);

    /** Latch footprints on the lid in model pixels (model faces north): latch i is centred on x = 11 - 3i. */
    public static final double LATCH_Y0 = 14, LATCH_Y1 = 15, LATCH_Z0 = 4, LATCH_Z1 = 7, LATCH_HALF_WIDTH = 1;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        VoxelShape base = Block.box(1, 0, 1, 15, 14, 15);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = base;
            for (int latch = 0; latch < 3; latch++) {
                double cx = latchCenter(latch);
                shape = Shapes.or(shape, rotatedBox(facing, cx - LATCH_HALF_WIDTH, LATCH_Y0, LATCH_Z0, cx + LATCH_HALF_WIDTH, LATCH_Y1, LATCH_Z1));
            }
            SHAPES.put(facing, shape.optimize());
        }
    }

    public LinkedVaultBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    public static double latchCenter(int latch) {
        return 11 - 3 * latch;
    }

    /** A box given in north-facing model pixels, rotated like the block model for {@code facing}. */
    static VoxelShape rotatedBox(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    /** Which latch (0 = left as seen from the front) a hit lands on, or -1. */
    public static int latchAt(BlockState state, BlockPos pos, Vec3 hit) {
        double lx = hit.x - pos.getX();
        double ly = (hit.y - pos.getY()) * 16;
        double lz = hit.z - pos.getZ();
        if (ly < LATCH_Y0 - 0.01) {
            return -1;
        }
        double cx = lx - 0.5;
        double cz = lz - 0.5;
        double mx;
        double mz;
        switch (state.getValue(FACING)) {
            case EAST -> {
                mx = cz;
                mz = -cx;
            }
            case SOUTH -> {
                mx = -cx;
                mz = -cz;
            }
            case WEST -> {
                mx = -cz;
                mz = cx;
            }
            default -> {
                mx = cx;
                mz = cz;
            }
        }
        mx = (mx + 0.5) * 16;
        mz = (mz + 0.5) * 16;
        if (mz < LATCH_Z0 - 0.75 || mz > LATCH_Z1 + 0.75) {
            return -1;
        }
        for (int latch = 0; latch < 3; latch++) {
            if (Math.abs(mx - latchCenter(latch)) <= LATCH_HALF_WIDTH + 0.5) {
                return latch;
            }
        }
        return -1;
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
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkedVaultBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, PhysicalContent.LINKED_BE.get(), LinkedVaultBlockEntity::clientTick)
                : createTickerHelper(type, PhysicalContent.LINKED_BE.get(), LinkedVaultBlockEntity::serverTick);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof LinkedVaultBlockEntity vault) {
            vault.lid().recheck();
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LinkedVaultBlockEntity vault)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof DyeItem dye) {
            int latch = latchAt(state, pos, hit.getLocation());
            if (latch >= 0) {
                return recolor(vault, latch, dye, stack, level, pos, player);
            }
        }
        if (stack.is(Items.DIAMOND) && PhysicalConfig.LINKED_PRIVATE.get()) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            boolean wasPrivate = vault.frequency().isPrivate();
            if (togglePrivate(vault, level, pos, player) && !wasPrivate && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (BlockUseOverride.applies(stack, level, pos, state, player)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static ItemInteractionResult recolor(LinkedVaultBlockEntity vault, int latch, DyeItem dye, ItemStack stack, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!vault.canUse(player)) {
            denyPrivate(vault, player);
            return ItemInteractionResult.CONSUME;
        }
        LinkedFrequency current = vault.frequency();
        if (current.color(latch) != dye.getDyeColor()) {
            vault.setFrequency(current.withColor(latch, dye.getDyeColor()));
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.translatable("message.omnivault.linked.frequency", vault.frequency().describe()), true);
        }
        return ItemInteractionResult.CONSUME;
    }

    /** @return true if the vault changed. */
    private static boolean togglePrivate(LinkedVaultBlockEntity vault, Level level, BlockPos pos, Player player) {
        LinkedFrequency current = vault.frequency();
        if (current.isPrivate()) {
            if (!vault.canUse(player)) {
                denyPrivate(vault, player);
                return false;
            }
            vault.setFrequency(current.asPublic());
            player.displayClientMessage(Component.translatable("message.omnivault.linked.public").withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7f, 1.4f);
        } else {
            vault.setFrequency(current.withOwner(player.getUUID(), player.getGameProfile().getName()));
            player.displayClientMessage(Component.translatable("message.omnivault.linked.private").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 0.8f);
        }
        return true;
    }

    private static void denyPrivate(LinkedVaultBlockEntity vault, Player player) {
        String owner = vault.frequency().ownerName();
        player.displayClientMessage(Component.translatable("message.omnivault.linked.not_owner", owner.isEmpty() ? "?" : owner)
                .withStyle(ChatFormatting.RED), true);
    }

    @Override
    public InteractionResult onWrench(Level level, BlockPos pos, BlockState state, Player player, Direction clickedFace) {
        if (!PhysicalConfig.LINKED_PRIVATE.get() || !(level.getBlockEntity(pos) instanceof LinkedVaultBlockEntity vault)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            togglePrivate(vault, level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof LinkedVaultBlockEntity vault) {
            if (!vault.canUse(player)) {
                denyPrivate(vault, player);
                return InteractionResult.CONSUME;
            }
            vault.openMenu(serverPlayer);
            player.awardStat(Stats.OPEN_ENDERCHEST);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof LinkedVaultBlockEntity vault && vault.inventory() != null) {
            return AbstractContainerMenu.getRedstoneSignalFromContainer(vault.inventory());
        }
        return 0;
    }
}
