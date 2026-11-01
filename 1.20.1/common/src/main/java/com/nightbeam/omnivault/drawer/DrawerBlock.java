package com.nightbeam.omnivault.drawer;

import com.nightbeam.omnivault.content.CoreContent;
import com.nightbeam.omnivault.content.ItemInteractionResult;
import com.nightbeam.omnivault.content.Wrenchable;
import com.nightbeam.omnivault.registry.ImplicitComponents;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.util.ItemStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A drawer (1×1, 1×2, 2×2 or compacting). Front face: right-click inserts, a quick second right-click
 * inserts everything matching from the inventory, left-click takes a stack (sneak: one item).
 * Sneak-right-click with an empty hand opens the upgrade menu; the Drawer Key or OmniTool on the front
 * toggles the lock; right-clicking with a drawer upgrade installs it.
 */
public class DrawerBlock extends BaseEntityBlock implements Wrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final DrawerLayout layout;

    public DrawerBlock(DrawerLayout layout, Properties properties) {
        super(properties);
        this.layout = layout;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public DrawerLayout layout() {
        return layout;
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
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return layout == DrawerLayout.COMPACTING ? new CompactingDrawerBlockEntity(pos, state) : new DrawerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ImplicitComponents.applyOnPlace(level, pos, stack);
    }

    @Nullable
    private static AbstractDrawerBlockEntity drawerAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AbstractDrawerBlockEntity drawer ? drawer : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.use(useItemOn(player.getItemInHand(hand), state, level, pos, player, hand, hit), hand,
                () -> useWithoutItem(state, level, pos, player, hit));
    }

    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(CoreContent.OMNI_TOOL.get())) {
            // Let the OmniTool's own use run: it calls onWrench (lock) or rotates / dismantles.
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        AbstractDrawerBlockEntity drawer = drawerAt(level, pos);
        if (drawer == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(DrawerContent.DRAWER_KEY.get())) {
            if (!level.isClientSide) {
                DrawerInteraction.toggleLock(drawer, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        int slot = DrawerInteraction.compartmentAt(state, pos, hit);
        if (stack.getItem() instanceof DrawerUpgradeItem && !(slot >= 0 && ItemKey.of(stack).equals(drawer.keyAt(slot)))) {
            if (!level.isClientSide) {
                if (drawer.installUpgrade(stack)) {
                    ItemStacks.consume(stack, 1, player);
                    player.displayClientMessage(Component.translatable("message.omnivault.drawer.upgrade_installed", stack.getHoverName()), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.omnivault.drawer.upgrade_failed"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (slot < 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            DrawerInteraction.rightClick(level, drawer, player, hand, slot);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        AbstractDrawerBlockEntity drawer = drawerAt(level, pos);
        if (drawer == null) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer serverPlayer) {
                drawer.openMenu(serverPlayer);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        int slot = DrawerInteraction.compartmentAt(state, pos, hit);
        if (slot < 0 || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            DrawerInteraction.rightClick(level, drawer, player, InteractionHand.MAIN_HAND, slot);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        DrawerInteraction.leftClick(level, pos, state, player);
    }

    @Override
    public InteractionResult onWrench(Level level, BlockPos pos, BlockState state, Player player, Direction clickedFace) {
        if (clickedFace != state.getValue(FACING) || drawerAt(level, pos) == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            DrawerInteraction.toggleLock(drawerAt(level, pos), player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Like shulker boxes: creative players breaking a drawer that holds something still get it as an item. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        AbstractDrawerBlockEntity drawer = drawerAt(level, pos);
        if (drawer != null && !level.isClientSide && player.isCreative() && drawer.hasData()) {
            ItemStack stack = new ItemStack(this);
            drawer.collectImplicitComponents(stack);
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        AbstractDrawerBlockEntity drawer = drawerAt(level, pos);
        return drawer == null ? 0 : drawer.comparatorSignal();
    }
}
