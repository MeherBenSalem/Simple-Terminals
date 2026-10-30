package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.StorageProvider;
import com.nightbeam.omnivault.network.api.TickingNode;
import com.nightbeam.omnivault.network.cell.CellData;
import com.nightbeam.omnivault.network.cell.CellStorage;
import com.nightbeam.omnivault.network.cell.CellUsage;
import com.nightbeam.omnivault.network.cell.StorageCellItem;
import com.nightbeam.omnivault.storage.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import com.nightbeam.omnivault.network.menu.DriveMenu;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Holds up to ten storage cells and adds them to the network. */
public class DriveBlockEntity extends NetworkNodeBlockEntity implements StorageProvider, TickingNode, MenuDevice {
    public static final int SLOTS = 10;

    private final SimpleContainer cells = new SimpleContainer(SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof StorageCellItem;
        }
    };
    private int priority;
    private boolean usageDirty = true;

    public DriveBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.DRIVE_BE.get(), pos, state);
        cells.addListener(container -> onCellsChanged());
    }

    public SimpleContainer cells() {
        return cells;
    }

    public int priority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Math.max(-999, Math.min(999, priority));
        setChanged();
        StorageNetwork network = getNetwork();
        if (network != null) {
            network.invalidateStorages();
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider(
                (id, inventory, p) -> new DriveMenu(id, inventory, this), getBlockState().getBlock().getName()),
                buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, cells);
    }

    private void onCellsChanged() {
        setChanged();
        usageDirty = true;
        StorageNetwork network = getNetwork();
        if (network != null) {
            network.invalidateStorages();
        }
        sync();
    }

    @Override
    public int energyUsage() {
        int used = 2;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!cells.getItem(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    @Override
    public void collectStorages(Consumer<ItemStorage> out) {
        if (level == null || level.getServer() == null) {
            return;
        }
        CellData data = CellData.get(level.getServer());
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = cells.getItem(slot);
            if (stack.getItem() instanceof StorageCellItem cell) {
                CellData.CellInventory inventory = data.getOrCreate(StorageCellItem.getOrCreateId(stack));
                out.accept(new CellStorage(inventory, cell.tier(), priority, () -> usageDirty = true));
            }
        }
    }

    @Override
    public long capacity() {
        long total = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (cells.getItem(slot).getItem() instanceof StorageCellItem cell && !cell.tier().isCreative()) {
                total += cell.tier().capacity;
            }
        }
        return total;
    }

    @Override
    public void networkTick(StorageNetwork network, long gameTime) {
        if (usageDirty && gameTime % 10 == 0) {
            refreshUsage();
        }
    }

    /** Mirrors each cell's contents summary onto its stack and re-syncs the LEDs. */
    public void refreshUsage() {
        usageDirty = false;
        if (level == null || level.getServer() == null) {
            return;
        }
        CellData data = CellData.get(level.getServer());
        boolean changed = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = cells.getItem(slot);
            if (stack.getItem() instanceof StorageCellItem) {
                CellData.CellInventory inventory = data.getOrCreate(StorageCellItem.getOrCreateId(stack));
                CellUsage usage = new CellUsage(inventory.stored(), inventory.types());
                if (!usage.equals(stack.get(NetworkContent.CELL_USAGE.get()))) {
                    stack.set(NetworkContent.CELL_USAGE.get(), usage);
                    changed = true;
                }
            }
        }
        if (changed) {
            setChanged();
            sync();
        }
    }

    @Override
    public void onNetworkStateChanged() {
        super.onNetworkStateChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, cells.getItems(), registries);
        tag.putInt("priority", priority);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> loaded = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, loaded, registries);
        for (int slot = 0; slot < SLOTS; slot++) {
            cells.getItems().set(slot, loaded.get(slot));
        }
        priority = tag.getInt("priority");
        clientOnline = tag.getBoolean("online");
        usageDirty = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ContainerHelper.saveAllItems(tag, cells.getItems(), true, registries);
        tag.putBoolean("online", isOnline());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Client copy of the online flag, for the LED renderer. */
    private boolean clientOnline;

    public boolean clientOnline() {
        return clientOnline;
    }
}
