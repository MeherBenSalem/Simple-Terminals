package com.nightbeam.omnivault.autocraft.block;

import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.CraftingManager;
import com.nightbeam.omnivault.autocraft.job.CraftingJob;
import com.nightbeam.omnivault.autocraft.menu.CraftingStatusMenu;
import com.nightbeam.omnivault.autocraft.pattern.ResolvedPattern;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.block.MenuDevice;
import com.nightbeam.omnivault.network.block.NetworkNodeBlockEntity;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Gives its network room for one crafting job and keeps that job (with every item it holds) safe
 * across saves. Without any processor, craft requests are refused.
 */
public class CraftingProcessorBlockEntity extends NetworkNodeBlockEntity implements MenuDevice {
    @Nullable
    private CraftingJob job;

    public CraftingProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(AutocraftContent.CRAFTING_PROCESSOR_BE.get(), pos, state);
    }

    @Nullable
    public CraftingJob job() {
        return job;
    }

    public boolean isBusy() {
        return job != null;
    }

    public void setJob(@Nullable CraftingJob job) {
        this.job = job;
        setChanged();
        updateBusy();
    }

    private void updateBusy() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        boolean busy = job != null;
        if (state.hasProperty(AutocraftDeviceBlock.WORKING) && state.getValue(AutocraftDeviceBlock.WORKING) != busy) {
            level.setBlock(worldPosition, state.setValue(AutocraftDeviceBlock.WORKING, busy), 3);
        }
    }

    @Override
    public void setNetwork(@Nullable StorageNetwork network) {
        super.setNetwork(network);
        CraftingManager.devicesChanged();
    }

    @Override
    public int energyUsage() {
        return 6;
    }

    @Override
    public void openMenu(ServerPlayer player) {
        CraftingStatusMenu.open(player, this::getNetwork,
                p -> !isRemoved() && p.distanceToSqr(worldPosition.getCenter()) <= 64, null);
    }

    /** Broken with a job inside: everything it holds goes back to the network, or drops if it cannot. */
    @Override
    public void dropContents(Level level, BlockPos pos) {
        if (job == null) {
            return;
        }
        StorageNetwork network = getNetwork();
        List<ResolvedPattern.KeyAmount> held = job.heldSnapshot();
        job.cancel();
        for (ResolvedPattern.KeyAmount entry : held) {
            ItemKey key = entry.key();
            long left = entry.amount();
            if (network != null && network.isActive()) {
                left -= network.storage().insert(key, left, false);
            }
            while (left > 0) {
                int count = (int) Math.min(left, key.maxStackSize());
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, key.toStack(count));
                left -= count;
            }
        }
        job = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (job != null) {
            tag.put("job", job.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        job = tag.contains("job") ? CraftingJob.load(tag.getCompound("job"), registries) : null;
    }
}
