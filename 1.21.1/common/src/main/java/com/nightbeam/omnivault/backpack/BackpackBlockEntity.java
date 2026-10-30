package com.nightbeam.omnivault.backpack;

import com.nightbeam.omnivault.backpack.upgrade.BackpackUpgrades;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A placed backpack: holds the backpack item (and so its UUID) and exposes the contents to automation. */
public class BackpackBlockEntity extends BlockEntity implements ModCapabilities.ItemProvider {
    private ItemStack backpack = ItemStack.EMPTY;
    /** Contents version comparators last saw (not saved). */
    private long seenVersion = -1;

    public BackpackBlockEntity(BlockPos pos, BlockState state) {
        super(BackpackContent.BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStack getBackpack() {
        return backpack;
    }

    public void setBackpack(ItemStack stack) {
        backpack = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** The item this block drops: the stored backpack (same UUID, same contents), or a fresh one. */
    public ItemStack dropStack() {
        if (backpack.isEmpty()) {
            return new ItemStack(getBlockState().getBlock());
        }
        ItemStack drop = backpack.copy();
        if (level instanceof ServerLevel serverLevel) {
            BackpackData data = Backpacks.existingData(serverLevel.getServer(), drop);
            if (data != null) {
                Backpacks.refreshSummary(drop, data);
            }
        }
        return drop;
    }

    /** The contents (server only), giving the stored backpack a UUID if it has none yet. */
    @Nullable
    public BackpackData data() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (backpack.isEmpty()) {
            backpack = new ItemStack(getBlockState().getBlock());
        }
        boolean fresh = Backpacks.getId(backpack) == null;
        BackpackData data = Backpacks.data(serverLevel.getServer(), backpack);
        if (fresh) {
            setChanged();
        }
        return data;
    }

    /** Lets comparators re-read the fill level after the contents changed (hoppers, pickups, other players). */
    public void notifyIfContentsChanged(BackpackData data) {
        if (data.version() != seenVersion && level != null) {
            seenVersion = data.version();
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public int color() {
        return DyedItemColor.getOrDefault(backpack, DyedItemColor.LEATHER_COLOR);
    }

    @Override
    @Nullable
    public SlotAccess getSlotAccess(@Nullable Direction side) {
        BackpackData data = data();
        return data == null ? null : new BackpackSlotAccess(data.items());
    }

    public int analogSignal() {
        BackpackData data = data();
        if (data == null) {
            return 0;
        }
        BackpackInventory items = data.items();
        float fill = 0;
        for (int slot = 0; slot < items.getContainerSize(); slot++) {
            ItemStack stack = items.getItem(slot);
            if (!stack.isEmpty()) {
                fill += (float) stack.getCount() / Math.min(items.getMaxStackSize(), stack.getMaxStackSize());
            }
        }
        fill /= Math.max(1, items.getContainerSize());
        return fill <= 0 ? 0 : 1 + Math.round(fill * 14);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BackpackBlockEntity blockEntity) {
        if (level instanceof ServerLevel serverLevel && !blockEntity.backpack.isEmpty() && Backpacks.getId(blockEntity.backpack) != null) {
            BackpackUpgrades.tickPlaced(serverLevel, pos, blockEntity);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!backpack.isEmpty()) {
            tag.put("Backpack", backpack.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        backpack = tag.contains("Backpack") ? ItemStack.parseOptional(registries, tag.getCompound("Backpack")) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
