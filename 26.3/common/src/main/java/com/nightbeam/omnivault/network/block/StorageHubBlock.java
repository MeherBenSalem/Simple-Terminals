package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Storage Hub: a cheap network root for the early game. Chests, barrels and any other inventory
 * touching the hub or an Inventory Cable connected to it show up together in a Storage Terminal.
 */
public class StorageHubBlock extends NexusCoreBlock {
    public StorageHubBlock(Properties properties) {
        super(properties);
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

    /** The hub's item, which explains what a hub network can and cannot do. */
    public static BlockItem item(StorageHubBlock block, Item.Properties properties) {
        return new BlockItem(block, properties) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
                tooltip.accept(Component.translatable("tooltip.omnivault.storage_hub").withStyle(ChatFormatting.GRAY));
                tooltip.accept(Component.translatable("tooltip.omnivault.storage_hub.limits").withStyle(ChatFormatting.DARK_GRAY));
            }
        };
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        // A chest placed or removed next to the hub changes the network's storage.
        NetworkManager.queueUpdate(level, pos);
    }
}
