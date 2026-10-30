package com.nightbeam.omnivault.drawer;

import com.mojang.serialization.MapCodec;
import com.nightbeam.omnivault.content.CoreContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Drawer Controller. Front: right-click inserts the held stack into the connected drawers, a quick second
 * click inserts every inventory item they already hold. Drawer Key: locks / unlocks every drawer.
 * Sneak-right-click: shows how many drawers are connected.
 */
public class DrawerControllerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = DrawerBlock.FACING;
    public static final MapCodec<DrawerControllerBlock> CODEC = simpleCodec(DrawerControllerBlock::new);

    public DrawerControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrawerControllerBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(CoreContent.OMNI_TOOL.get()) || !(level.getBlockEntity(pos) instanceof DrawerControllerBlockEntity controller)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(DrawerContent.DRAWER_KEY.get())) {
            if (!level.isClientSide) {
                boolean locked = controller.toggleLockAll();
                player.displayClientMessage(Component.translatable(locked ? "message.omnivault.drawer_controller.locked"
                        : "message.omnivault.drawer_controller.unlocked", controller.drawers().size()), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (hit.getDirection() != state.getValue(FACING)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            click(level, pos, controller, player, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DrawerControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                int compartments = controller.drawers().stream().mapToInt(AbstractDrawerBlockEntity::compartments).sum();
                player.displayClientMessage(Component.translatable("message.omnivault.drawer_controller.status",
                        controller.drawers().size(), compartments), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (hit.getDirection() != state.getValue(FACING) || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            click(level, pos, controller, player, ItemStack.EMPTY);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void click(Level level, BlockPos pos, DrawerControllerBlockEntity controller, Player player, ItemStack held) {
        boolean second = controller.registerClick(player, level.getGameTime());
        long inserted = second ? controller.insertInventory(player) : controller.insertHeld(player, held);
        if (inserted > 0) {
            level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.getBlockEntity(pos) instanceof DrawerControllerBlockEntity controller) {
            controller.invalidate();
        }
    }
}
