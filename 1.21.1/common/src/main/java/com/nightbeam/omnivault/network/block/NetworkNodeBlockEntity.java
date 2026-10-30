package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkManager;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/** Base block entity for network devices. */
public abstract class NetworkNodeBlockEntity extends BlockEntity implements NetworkNode {
    @Nullable
    private StorageNetwork network;

    protected NetworkNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    @Nullable
    public StorageNetwork getNetwork() {
        return network != null && network.isRemoved() ? null : network;
    }

    @Override
    public void setNetwork(@Nullable StorageNetwork network) {
        if (this.network != network) {
            this.network = network;
            onNetworkStateChanged();
        }
    }

    /** True while connected to a running network. */
    public boolean isOnline() {
        StorageNetwork current = getNetwork();
        return current != null && current.isActive();
    }

    @Override
    public void onNetworkStateChanged() {
        updateActiveProperty(DeviceBlock.ACTIVE);
    }

    protected void updateActiveProperty(BooleanProperty property) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(property) && state.getValue(property) != isOnline()) {
            level.setBlock(worldPosition, state.setValue(property, isOnline()), 3);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        StorageNetwork current = getNetwork();
        if (current != null) {
            current.markDirty();
        }
        NetworkManager.queueUpdate(level, worldPosition);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        NetworkManager.queueUpdate(level, worldPosition);
    }
}
