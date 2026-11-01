package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.content.ItemInteractionResult;
import com.nightbeam.omnivault.network.NetworkManager;
import com.nightbeam.omnivault.network.api.NetworkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

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

    /** Runs {@link #useItemOn} and {@link #useWithoutItem}, which devices override as on later versions. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.use(useItemOn(player.getItemInHand(hand), state, level, pos, player, hand, hit), hand,
                () -> useWithoutItem(state, level, pos, player, hit));
    }

    /** Right-click with an item; passes to {@link #useWithoutItem} unless a device says otherwise. */
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Right-click with an empty hand, or with an item that passed to it. */
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock()) || connectionsDiffer(oldState, state)) {
            NetworkManager.queueUpdate(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
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
