package com.nightbeam.omnivault.network.block;

import com.mojang.serialization.MapCodec;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Storage Hub: a cheap network root for the early game. Chests, barrels and any other inventory
 * touching the hub or an Inventory Cable connected to it show up together in a Storage Terminal.
 */
public class StorageHubBlock extends NexusCoreBlock {
    public static final MapCodec<StorageHubBlock> CODEC = simpleCodec(StorageHubBlock::new);

    public StorageHubBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageHubBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends NexusCoreBlockEntity> blockEntityType() {
        return NetworkContent.STORAGE_HUB_BE.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.omnivault.storage_hub").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.omnivault.storage_hub.limits").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        // A chest placed or removed next to the hub changes the network's storage.
        NetworkManager.queueUpdate(level, pos);
    }
}
