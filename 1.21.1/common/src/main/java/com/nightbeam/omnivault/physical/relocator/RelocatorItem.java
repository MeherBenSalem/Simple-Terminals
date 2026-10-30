package com.nightbeam.omnivault.physical.relocator;

import com.nightbeam.omnivault.physical.BlockUseOverride;
import com.nightbeam.omnivault.physical.PhysicalConfig;
import com.nightbeam.omnivault.physical.PhysicalContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Packing-tape-style mover: right-click a block that has a block entity to pick it up with everything
 * inside (state + block entity data are kept in a data component), right-click again to put it down.
 * The iron version wears out after a configurable number of moves; the netherite one is reusable.
 */
public class RelocatorItem extends Item implements BlockUseOverride {
    private final boolean reusable;

    public RelocatorItem(boolean reusable, Properties properties) {
        super(properties);
        this.reusable = reusable;
    }

    @Nullable
    public static RelocatedBlock carried(ItemStack stack) {
        return stack.get(PhysicalContent.RELOCATED_BLOCK.get());
    }

    @Override
    public boolean overridesBlockUse(ItemStack stack, Level level, BlockPos pos, BlockState state, Player player) {
        return carried(stack) != null || refusal(level, pos, state, player) == null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        RelocatedBlock carried = carried(stack);
        return carried == null ? pickUp(context, stack) : place(context, stack, carried);
    }

    /**
     * Why the block at {@code pos} cannot be picked up, or null if it can.
     */
    @Nullable
    public static Component refusal(Level level, BlockPos pos, BlockState state, @Nullable Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return Component.translatable("message.omnivault.relocator.no_block_entity");
        }
        if (!PhysicalConfig.RELOCATOR_ENABLED.get()) {
            return Component.translatable("message.omnivault.relocator.disabled");
        }
        if (state.is(PhysicalContent.RELOCATOR_DENYLIST) || state.getDestroySpeed(level, pos) < 0) {
            return Component.translatable("message.omnivault.relocator.denied", state.getBlock().getName());
        }
        if (player != null && (!player.mayBuild() || !level.mayInteract(player, pos))) {
            return Component.translatable("message.omnivault.relocator.no_permission");
        }
        return null;
    }

    private InteractionResult pickUp(UseOnContext context, ItemStack stack) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        Component refusal = refusal(level, pos, state, player);
        if (refusal != null) {
            if (level.getBlockEntity(pos) == null) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag data = blockEntity.saveWithoutMetadata(level.registryAccess());
        // Empty the block entity and remove it before the block, so nothing spills or duplicates.
        Clearable.tryClear(blockEntity);
        level.removeBlockEntity(pos);
        level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), Block.UPDATE_ALL);
        stack.set(PhysicalContent.RELOCATED_BLOCK.get(), new RelocatedBlock(state, data));
        level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
        level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0f, 0.7f);
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.6f, 1.0f);
        return InteractionResult.CONSUME;
    }

    private InteractionResult place(UseOnContext context, ItemStack stack, RelocatedBlock carried) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        BlockPos target = placeContext.getClickedPos();
        if (!placeContext.canPlace()) {
            return InteractionResult.FAIL;
        }
        BlockState state = orient(carried.state(), placeContext, level, target);
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        if (!state.canSurvive(level, target) || !level.isUnobstructed(state, target, collision)
                || (player != null && !player.mayUseItemAt(target, context.getClickedFace(), stack))) {
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable("message.omnivault.relocator.blocked").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!level.setBlock(target, state, Block.UPDATE_ALL)) {
            return InteractionResult.FAIL;
        }
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (blockEntity != null) {
            blockEntity.loadWithComponents(carried.data(), level.registryAccess());
            blockEntity.setChanged();
            level.sendBlockUpdated(target, state, state, Block.UPDATE_ALL);
        }
        stack.remove(PhysicalContent.RELOCATED_BLOCK.get());
        level.gameEvent(player, GameEvent.BLOCK_PLACE, target);
        level.playSound(null, target, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.9f);
        level.playSound(null, target, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8f, 1.1f);
        if (!reusable) {
            if (player != null) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
            } else if (level instanceof ServerLevel serverLevel) {
                stack.hurtAndBreak(1, serverLevel, (net.minecraft.server.level.ServerPlayer) null, item -> {
                });
            }
        }
        return InteractionResult.CONSUME;
    }

    /** Turns the stored block to face the player (like a fresh placement) and resets multi-block links. */
    private static BlockState orient(BlockState stored, BlockPlaceContext context, Level level, BlockPos target) {
        BlockState state = stored;
        Direction original = null;
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            original = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        } else if (state.hasProperty(BlockStateProperties.FACING) && state.getValue(BlockStateProperties.FACING).getAxis().isHorizontal()) {
            original = state.getValue(BlockStateProperties.FACING);
        }
        if (original != null) {
            Direction desired = context.getHorizontalDirection().getOpposite();
            int turns = Math.floorMod(desired.get2DDataValue() - original.get2DDataValue(), 4);
            state = state.rotate(Rotation.values()[turns]);
        }
        if (state.hasProperty(BlockStateProperties.CHEST_TYPE)) {
            state = state.setValue(BlockStateProperties.CHEST_TYPE, ChestType.SINGLE);
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(target).getType() == Fluids.WATER);
        }
        return Block.updateFromNeighbourShapes(state, level, target);
    }

    @Override
    public Component getName(ItemStack stack) {
        RelocatedBlock carried = carried(stack);
        if (carried != null) {
            return Component.translatable("item.omnivault.relocator.carrying", super.getName(stack), carried.state().getBlock().getName());
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RelocatedBlock carried = carried(stack);
        if (carried != null) {
            tooltip.add(Component.translatable("item.omnivault.relocator.contains", carried.state().getBlock().getName().copy()
                    .withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
            int stacks = carried.storedStacks();
            if (stacks >= 0) {
                tooltip.add(Component.translatable("item.omnivault.relocator.stacks", stacks).withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("item.omnivault.relocator.tooltip.place").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.omnivault.relocator.tooltip.pick").withStyle(ChatFormatting.GRAY));
        }
        if (reusable) {
            tooltip.add(Component.translatable("item.omnivault.relocator.tooltip.reusable").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
