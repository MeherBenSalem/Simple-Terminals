package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.item.UpgradeItem;
import com.nightbeam.omnivault.network.NetworkConfig;
import com.nightbeam.omnivault.network.menu.BusMenu;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Shared state for import, export and storage buses: a 9-slot ghost filter and upgrade slots. */
public abstract class BusBlockEntity extends NetworkNodeBlockEntity implements MenuDevice {
    public static final int FILTER_SLOTS = 9;
    public static final int UPGRADE_SLOTS = 4;

    protected final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };
    protected final SimpleContainer upgrades = new SimpleContainer(UPGRADE_SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem upgrade && acceptsUpgrade(upgrade.kind());
        }
    };
    protected boolean denyList;

    protected BusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        filter.addListener(c -> onConfigChanged());
        upgrades.addListener(c -> onConfigChanged());
    }

    protected void onConfigChanged() {
        setChanged();
    }

    public SimpleContainer filter() {
        return filter;
    }

    public SimpleContainer upgrades() {
        return upgrades;
    }

    public boolean denyList() {
        return denyList;
    }

    public void setDenyList(boolean denyList) {
        this.denyList = denyList;
        onConfigChanged();
    }

    /** Which kind of bus this is, for the shared screen. */
    public abstract BusMenu.Kind kind();

    protected boolean acceptsUpgrade(UpgradeItem.Kind kind) {
        return kind == UpgradeItem.Kind.SPEED || kind == UpgradeItem.Kind.STACK
                || kind == UpgradeItem.Kind.FUZZY || kind == UpgradeItem.Kind.REDSTONE;
    }

    public int upgradeCount(UpgradeItem.Kind kind) {
        int count = 0;
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            if (upgrades.getItem(slot).getItem() instanceof UpgradeItem upgrade && upgrade.kind() == kind) {
                count++;
            }
        }
        return Math.min(count, kind.maxPerDevice);
    }

    protected int interval() {
        return Math.max(1, NetworkConfig.BUS_INTERVAL.get() >> upgradeCount(UpgradeItem.Kind.SPEED));
    }

    protected int itemsPerOperation() {
        return NetworkConfig.BUS_ITEMS.get() << upgradeCount(UpgradeItem.Kind.STACK);
    }

    protected boolean redstoneAllows() {
        return upgradeCount(UpgradeItem.Kind.REDSTONE) == 0 || (level != null && level.hasNeighborSignal(worldPosition));
    }

    public boolean filterIsEmpty() {
        return filter.isEmpty();
    }

    /** Whether the filter lets {@code key} through. An empty allow-list lets everything through. */
    public boolean passes(ItemKey key) {
        boolean fuzzy = upgradeCount(UpgradeItem.Kind.FUZZY) > 0;
        boolean listed = false;
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            ItemStack entry = filter.getItem(slot);
            if (!entry.isEmpty() && (fuzzy ? entry.is(key.item()) : key.matches(entry))) {
                listed = true;
                break;
            }
        }
        if (filter.isEmpty()) {
            return true;
        }
        return denyList != listed;
    }

    public Direction facing() {
        return getBlockState().getValue(DirectionalDeviceBlock.FACING);
    }

    protected Optional<SlotAccess> target() {
        if (level == null) {
            return Optional.empty();
        }
        Direction facing = facing();
        return Services.PLATFORM.findSlotAccess(level, worldPosition.relative(facing), facing.getOpposite());
    }

    @Override
    public void openMenu(ServerPlayer player) {
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider(
                (id, inventory, p) -> new BusMenu(id, inventory, this), getBlockState().getBlock().getName()),
                buf -> {
                    buf.writeBlockPos(worldPosition);
                    buf.writeVarInt(kind().ordinal());
                });
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, upgrades);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("filter", ContainerHelper.saveAllItems(new CompoundTag(), filter.getItems(), registries));
        tag.put("upgrades", ContainerHelper.saveAllItems(new CompoundTag(), upgrades.getItems(), registries));
        tag.putBoolean("deny", denyList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        load(tag.getCompound("filter"), filter, registries);
        load(tag.getCompound("upgrades"), upgrades, registries);
        denyList = tag.getBoolean("deny");
    }

    private static void load(CompoundTag tag, SimpleContainer container, HolderLookup.Provider registries) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int slot = 0; slot < items.size(); slot++) {
            container.getItems().set(slot, items.get(slot));
        }
    }
}
