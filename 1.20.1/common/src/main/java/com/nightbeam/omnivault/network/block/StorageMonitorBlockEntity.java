package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.TickingNode;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Shows one item and how many the network holds; click to insert or take. */
public class StorageMonitorBlockEntity extends NetworkNodeBlockEntity implements TickingNode {
    private ItemStack displayed = ItemStack.EMPTY;
    private long count;

    public StorageMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.STORAGE_MONITOR_BE.get(), pos, state);
    }

    public ItemStack displayed() {
        return displayed;
    }

    public long count() {
        return count;
    }

    public void setDisplayed(ItemStack stack) {
        displayed = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        refresh();
    }

    @Override
    public void networkTick(StorageNetwork network, long gameTime) {
        if (gameTime % 10 == 0) {
            refresh();
        }
    }

    @Override
    public void onNetworkStateChanged() {
        super.onNetworkStateChanged();
        refresh();
    }

    private void refresh() {
        ItemKey key = ItemKey.of(displayed);
        StorageNetwork network = getNetwork();
        long now = key == null || network == null ? 0 : network.storage().getCount(key);
        if (now != count) {
            count = now;
            setChanged();
            forceSync();
        }
    }

    public void forceSync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!displayed.isEmpty()) {
            tag.put("displayed", displayed.save(new CompoundTag()));
        }
        tag.putLong("count", count);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        displayed = tag.contains("displayed") ? ItemStack.of(tag.getCompound("displayed")) : ItemStack.EMPTY;
        count = tag.getLong("count");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
