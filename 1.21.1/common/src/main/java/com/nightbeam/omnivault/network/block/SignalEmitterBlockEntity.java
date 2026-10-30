package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.TickingNode;
import com.nightbeam.omnivault.network.menu.SignalEmitterMenu;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class SignalEmitterBlockEntity extends NetworkNodeBlockEntity implements TickingNode, MenuDevice {
    private final SimpleContainer filter = new SimpleContainer(1) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };
    private long threshold = 64;
    /** false: emit while count >= threshold; true: emit while count < threshold. */
    private boolean below;

    public SignalEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.SIGNAL_EMITTER_BE.get(), pos, state);
        filter.addListener(c -> setChanged());
    }

    public SimpleContainer filter() {
        return filter;
    }

    public long threshold() {
        return threshold;
    }

    public void setThreshold(long threshold) {
        this.threshold = Math.max(0, threshold);
        setChanged();
    }

    public boolean below() {
        return below;
    }

    public void setBelow(boolean below) {
        this.below = below;
        setChanged();
    }

    @Override
    public void networkTick(StorageNetwork network, long gameTime) {
        if (gameTime % 10 == 0) {
            ItemKey key = ItemKey.of(filter.getItem(0));
            long count = key == null ? network.rawStorage().totalCount() : network.storage().getCount(key);
            setPowered(below ? count < threshold : count >= threshold);
        }
    }

    @Override
    public void onNetworkStateChanged() {
        if (!isOnline()) {
            setPowered(false);
        }
    }

    private void setPowered(boolean powered) {
        BlockState state = getBlockState();
        if (level != null && state.getValue(SignalEmitterBlock.POWERED) != powered) {
            level.setBlock(worldPosition, state.setValue(SignalEmitterBlock.POWERED, powered), Block.UPDATE_ALL);
            level.updateNeighborsAt(worldPosition, state.getBlock());
            BlockPos behind = worldPosition.relative(state.getValue(DirectionalDeviceBlock.FACING));
            level.updateNeighborsAt(behind, state.getBlock());
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider(
                (id, inventory, p) -> new SignalEmitterMenu(id, inventory, this), getBlockState().getBlock().getName()),
                buf -> {
                    buf.writeBlockPos(worldPosition);
                    buf.writeVarLong(threshold);
                    buf.writeBoolean(below);
                });
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!filter.getItem(0).isEmpty()) {
            tag.put("filter", filter.getItem(0).save(registries));
        }
        tag.putLong("threshold", threshold);
        tag.putBoolean("below", below);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filter.getItems().set(0, tag.contains("filter") ? ItemStack.parseOptional(registries, tag.getCompound("filter")) : ItemStack.EMPTY);
        threshold = tag.getLong("threshold");
        below = tag.getBoolean("below");
    }
}
