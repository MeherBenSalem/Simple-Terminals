package com.nightbeam.omnivault.item;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.content.Wrenchable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/** Wrench: right-click rotates, sneak-right-click dismantles any OmniVault block into an item. */
public class OmniToolItem extends Item {
    public OmniToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        boolean ours = OmniVault.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
        if (player.isShiftKeyDown() && ours) {
            if (level instanceof ServerLevel serverLevel) {
                Block.dropResources(state, serverLevel, pos, level.getBlockEntity(pos), player, context.getItemInHand());
                level.removeBlock(pos, false);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1.2f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getBlock() instanceof Wrenchable wrenchable) {
            InteractionResult result = wrenchable.onWrench(level, pos, state, player, context.getClickedFace());
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        if (!ours) {
            return InteractionResult.PASS;
        }
        BlockState rotated = rotate(state, context.getClickedFace());
        if (rotated != state && rotated.canSurvive(level, pos)) {
            if (!level.isClientSide) {
                level.setBlock(pos, rotated, Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static BlockState rotate(BlockState state, Direction clickedFace) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction current = state.getValue(BlockStateProperties.FACING);
            Direction next = current == clickedFace ? clickedFace.getOpposite() : clickedFace;
            return state.setValue(BlockStateProperties.FACING, next);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.rotate(Rotation.CLOCKWISE_90);
        }
        return state;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.omnivault.omni_tool.tooltip.rotate").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.omnivault.omni_tool.tooltip.dismantle").withStyle(ChatFormatting.GRAY));
    }
}
