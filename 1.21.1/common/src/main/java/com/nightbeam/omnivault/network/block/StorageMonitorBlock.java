package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Right-click with an item to choose what it shows; then right-click with that item to store it and
 * right-click empty-handed to take a stack (sneak for one). Sneak-right-click empty-handed with
 * nothing stored clears it.
 */
public class StorageMonitorBlock extends OrientedDeviceBlock {
    public StorageMonitorBlock(Properties properties) {
        super(StorageMonitorBlockEntity::new, properties);
    }

    private static boolean onFront(BlockState state, BlockHitResult hit) {
        return hit.getDirection() == state.getValue(FACING);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!onFront(state, hit) || !(level.getBlockEntity(pos) instanceof StorageMonitorBlockEntity monitor)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            if (monitor.displayed().isEmpty()) {
                monitor.setDisplayed(stack);
                monitor.forceSync();
            } else if (ItemStack.isSameItemSameComponents(monitor.displayed(), stack)) {
                StorageNetwork network = monitor.getNetwork();
                if (network != null && network.isActive()) {
                    ItemStack rest = network.insert(stack.copy());
                    player.setItemInHand(hand, rest);
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1.4f);
                }
            } else {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!onFront(state, hit) || !(level.getBlockEntity(pos) instanceof StorageMonitorBlockEntity monitor)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemKey key = ItemKey.of(monitor.displayed());
            StorageNetwork network = monitor.getNetwork();
            if (key != null && player.isShiftKeyDown() && monitor.count() == 0) {
                monitor.setDisplayed(ItemStack.EMPTY);
                monitor.forceSync();
            } else if (key != null && network != null && network.isActive()) {
                ItemStack taken = network.extract(key, player.isShiftKeyDown() ? 1 : key.maxStackSize());
                if (!taken.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(taken);
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1.0f);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
