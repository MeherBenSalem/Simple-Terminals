package com.nightbeam.omnivault.network.block;

import com.mojang.serialization.MapCodec;
import com.nightbeam.omnivault.content.BlockUseOverride;
import com.nightbeam.omnivault.content.Wrenchable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Entangle two bridges with a Quantum Link Card; the OmniTool breaks the link. */
public class QuantumBridgeBlock extends DeviceBlock implements Wrenchable {
    public static final MapCodec<QuantumBridgeBlock> CODEC = simpleCodec(QuantumBridgeBlock::new);

    public QuantumBridgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumBridgeBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // The Link Card and the OmniTool act on the bridge; anything else shows its status.
        if (BlockUseOverride.applies(stack, level, pos, state, player)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof QuantumBridgeBlockEntity bridge) {
            GlobalPos partner = bridge.partner();
            Component message = partner == null
                    ? Component.translatable("message.omnivault.bridge.unlinked")
                    : Component.translatable(bridge.linkedPartner() != null ? "message.omnivault.bridge.linked" : "message.omnivault.bridge.waiting",
                    partner.pos().getX(), partner.pos().getY(), partner.pos().getZ(), partner.dimension().location().toString());
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult onWrench(Level level, BlockPos pos, BlockState state, Player player, Direction clickedFace) {
        if (!(level.getBlockEntity(pos) instanceof QuantumBridgeBlockEntity bridge) || bridge.partner() == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            unlink(bridge);
            player.displayClientMessage(Component.translatable("message.omnivault.bridge.unlinked").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Clears both halves of a pair. */
    public static void unlink(QuantumBridgeBlockEntity bridge) {
        QuantumBridgeBlockEntity other = bridge.linkedPartner();
        bridge.setPartner(null);
        if (other != null) {
            other.setPartner(null);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel && level.getBlockEntity(pos) instanceof QuantumBridgeBlockEntity bridge) {
            QuantumBridgeBlockEntity other = bridge.linkedPartner();
            if (other != null) {
                other.setPartner(null);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
