package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.item.UpgradeItem;
import com.nightbeam.omnivault.network.NetworkConfig;
import com.nightbeam.omnivault.network.menu.BusMenu;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.FluidAccess;
import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.SlotAccess;
import com.nightbeam.omnivault.util.ObservedContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;

/** Shared state for import, export and storage buses: a 9-slot ghost filter and upgrade slots. */
public abstract class BusBlockEntity extends NetworkNodeBlockEntity implements MenuDevice {
    public static final int FILTER_SLOTS = 9;
    public static final int UPGRADE_SLOTS = 4;

    protected final ObservedContainer filter = new ObservedContainer(FILTER_SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };
    protected final ObservedContainer upgrades = new ObservedContainer(UPGRADE_SLOTS) {
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

    /**
     * Whether the filter lets a fluid through. Fluids are listed as filled buckets; a filter with no
     * buckets lets every fluid through (deny list) or none (allow list with items only).
     */
    public boolean passesFluid(FluidKey key) {
        boolean anyBucket = false;
        boolean listed = false;
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            ItemStack entry = filter.getItem(slot);
            FluidKey listedFluid = FluidKey.fromBucket(entry.getItem());
            if (listedFluid != null) {
                anyBucket = true;
                if (listedFluid.equals(key)) {
                    listed = true;
                }
            }
        }
        if (!anyBucket) {
            return filter.isEmpty() || denyList;
        }
        return denyList != listed;
    }

    protected Optional<FluidAccess> fluidTarget() {
        if (level == null) {
            return Optional.empty();
        }
        Direction facing = facing();
        return Services.PLATFORM.findFluidAccess(level, worldPosition.relative(facing), facing.getOpposite());
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output.child("filter"), filter.getItems());
        ContainerHelper.saveAllItems(output.child("upgrades"), upgrades.getItems());
        output.putBoolean("deny", denyList);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        load(input.childOrEmpty("filter"), filter);
        load(input.childOrEmpty("upgrades"), upgrades);
        denyList = input.getBooleanOr("deny", false);
    }

    private static void load(ValueInput input, SimpleContainer container) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        for (int slot = 0; slot < items.size(); slot++) {
            container.getItems().set(slot, items.get(slot));
        }
    }
}
