package com.nightbeam.omnivault.drawer;

import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared state of every drawer: upgrades, the lock, client sync, the item handler and the data
 * components that keep everything on the item when the drawer is broken. Subclasses decide how
 * compartments store items.
 */
public abstract class AbstractDrawerBlockEntity extends BlockEntity implements ModCapabilities.ItemProvider, MenuProvider {
    public static final int UPGRADE_SLOTS = 4;

    private final UpgradeContainer upgrades = new UpgradeContainer();
    private final DrawerAccess access = new DrawerAccess(this);
    private boolean locked;
    private int multiplier = 1;
    private boolean voiding;
    private long version;
    private long lastClickTick = Long.MIN_VALUE / 2;
    @Nullable
    private UUID lastClickPlayer;

    protected AbstractDrawerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected static DrawerLayout layoutOf(BlockState state) {
        return state.getBlock() instanceof DrawerBlock drawer ? drawer.layout() : DrawerLayout.SINGLE;
    }

    public DrawerLayout layout() {
        return layoutOf(getBlockState());
    }

    // ---------------------------------------------------------------------------------------------
    // Compartments
    // ---------------------------------------------------------------------------------------------

    public abstract int compartments();

    /** The type a compartment holds or is locked to, or null if it takes anything. */
    @Nullable
    public abstract ItemKey keyAt(int slot);

    public abstract long countAt(int slot);

    /** How many items of its type a compartment fits with the given capacity multiplier. */
    public abstract long capacityAt(int slot, int multiplier);

    public long capacityAt(int slot) {
        return capacityAt(slot, multiplier);
    }

    /** Whether a compartment takes this type at all, ignoring how much room is left. */
    public abstract boolean accepts(int slot, ItemKey key);

    /** @return how many items were (or would be) taken; voided overflow counts as taken. */
    public abstract long insert(int slot, ItemKey key, long amount, boolean simulate);

    /** @return how many items were (or would be) removed. */
    public abstract long extract(int slot, long amount, boolean simulate);

    /** Forgets the types of empty compartments; called when the drawer is unlocked. */
    protected abstract void forgetEmptyTypes();

    /** Fill level 0..1 for comparators. */
    public abstract double fillFraction();

    /**
     * Inserts into the compartments that hold (or are locked to) this type; if none does, into the
     * first compartment that takes anything. Never spreads one type over several empty compartments.
     */
    public long insertAny(ItemKey key, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        long remaining = amount;
        boolean matched = false;
        for (int slot = 0; slot < compartments() && remaining > 0; slot++) {
            if (key.equals(keyAt(slot))) {
                matched = true;
                remaining -= insert(slot, key, remaining, simulate);
            }
        }
        if (!matched) {
            for (int slot = 0; slot < compartments(); slot++) {
                if (keyAt(slot) == null && accepts(slot, key)) {
                    remaining -= insert(slot, key, remaining, simulate);
                    break;
                }
            }
        }
        return amount - remaining;
    }

    /** @return true if some compartment holds or is locked to this type. */
    public boolean holds(ItemKey key) {
        for (int slot = 0; slot < compartments(); slot++) {
            if (key.equals(keyAt(slot))) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        for (int slot = 0; slot < compartments(); slot++) {
            if (countAt(slot) > 0) {
                return false;
            }
        }
        return true;
    }

    /** True when the item form would carry anything worth keeping (contents, lock or upgrades). */
    public boolean hasData() {
        if (locked || !upgrades.isEmpty()) {
            return true;
        }
        for (int slot = 0; slot < compartments(); slot++) {
            if (keyAt(slot) != null) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Lock and upgrades
    // ---------------------------------------------------------------------------------------------

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        if (this.locked == locked) {
            return;
        }
        this.locked = locked;
        if (!locked) {
            forgetEmptyTypes();
        }
        contentsChanged();
    }

    public int capacityMultiplier() {
        return multiplier;
    }

    public boolean isVoiding() {
        return voiding;
    }

    public SimpleContainer upgrades() {
        return upgrades;
    }

    /** Whether {@code stack} may go into upgrade slot {@code slot} given what is installed elsewhere. */
    public static boolean isValidUpgrade(SimpleContainer upgrades, int slot, ItemStack stack) {
        if (!(stack.getItem() instanceof DrawerUpgradeItem upgrade)) {
            return false;
        }
        if (upgrade.kind() == DrawerUpgradeItem.Kind.VOID) {
            for (int i = 0; i < upgrades.getContainerSize(); i++) {
                if (i != slot && upgrades.getItem(i).getItem() instanceof DrawerUpgradeItem other && other.kind() == DrawerUpgradeItem.Kind.VOID) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Installs one item of {@code stack} into the first free upgrade slot; the caller shrinks the stack. */
    public boolean installUpgrade(ItemStack stack) {
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            if (upgrades.getItem(slot).isEmpty() && isValidUpgrade(upgrades, slot, stack)) {
                upgrades.setItem(slot, stack.copyWithCount(1));
                return true;
            }
        }
        return false;
    }

    /** A capacity upgrade can only come out if everything stored still fits without it. */
    public boolean canRemoveUpgrade(int slot) {
        ItemStack stack = upgrades.getItem(slot);
        if (!(stack.getItem() instanceof DrawerUpgradeItem upgrade) || upgrade.multiplier() == 0) {
            return true;
        }
        int reduced = Math.max(1, sumMultipliers() - upgrade.multiplier());
        for (int i = 0; i < compartments(); i++) {
            if (countAt(i) > capacityAt(i, reduced)) {
                return false;
            }
        }
        return true;
    }

    private int sumMultipliers() {
        int sum = 0;
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            if (upgrades.getItem(slot).getItem() instanceof DrawerUpgradeItem upgrade) {
                sum += upgrade.multiplier();
            }
        }
        return sum;
    }

    private void recomputeUpgrades() {
        boolean anyVoid = false;
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            if (upgrades.getItem(slot).getItem() instanceof DrawerUpgradeItem upgrade && upgrade.kind() == DrawerUpgradeItem.Kind.VOID) {
                anyVoid = true;
            }
        }
        multiplier = Math.max(1, sumMultipliers());
        voiding = anyVoid;
    }

    // ---------------------------------------------------------------------------------------------
    // Interaction helpers
    // ---------------------------------------------------------------------------------------------

    /** Records a right-click; true when it is a second click by the same player inside the double-click window. */
    public boolean registerClick(Player player, long gameTime) {
        boolean second = player.getUUID().equals(lastClickPlayer) && gameTime - lastClickTick <= DrawerConfig.DOUBLE_CLICK_TICKS.get();
        lastClickTick = second ? Long.MIN_VALUE / 2 : gameTime;
        lastClickPlayer = player.getUUID();
        return second;
    }

    public int comparatorSignal() {
        double fill = fillFraction();
        return fill <= 0 ? 0 : 1 + (int) Math.floor(Math.min(1, fill) * 14);
    }

    public long version() {
        return version;
    }

    /** Marks the drawer dirty and sends its new contents to watching clients (batched per tick by the chunk). */
    protected void contentsChanged() {
        version++;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    @Nullable
    public SlotAccess getSlotAccess(@Nullable Direction side) {
        return access;
    }

    public DrawerAccess access() {
        return access;
    }

    // ---------------------------------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new DrawerMenu(containerId, inventory, this);
    }

    public void openMenu(ServerPlayer player) {
        Services.PLATFORM.openMenu(player, this, buf -> buf.writeBlockPos(worldPosition));
    }

    // ---------------------------------------------------------------------------------------------
    // Saving, syncing and item components
    // ---------------------------------------------------------------------------------------------

    protected abstract void saveContents(CompoundTag tag, HolderLookup.Provider registries);

    protected abstract void loadContents(CompoundTag tag, HolderLookup.Provider registries);

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (locked) {
            tag.putBoolean("Locked", true);
        }
        if (!upgrades.isEmpty()) {
            tag.put("Upgrades", ContainerHelper.saveAllItems(new CompoundTag(), upgrades.getItems(), registries));
        }
        saveContents(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        locked = tag.getBoolean("Locked");
        NonNullList<ItemStack> items = NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
        if (tag.contains("Upgrades")) {
            ContainerHelper.loadAllItems(tag.getCompound("Upgrades"), items, registries);
        }
        upgrades.replaceAll(items);
        loadContents(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        locked = Boolean.TRUE.equals(input.get(DrawerContent.DRAWER_LOCKED.get()));
        NonNullList<ItemStack> items = NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
        input.getOrDefault(DrawerContent.DRAWER_UPGRADES.get(), ItemContainerContents.EMPTY).copyInto(items);
        upgrades.replaceAll(items);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (locked) {
            components.set(DrawerContent.DRAWER_LOCKED.get(), true);
        }
        if (!upgrades.isEmpty()) {
            components.set(DrawerContent.DRAWER_UPGRADES.get(), ItemContainerContents.fromItems(upgrades.getItems()));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Locked");
        tag.remove("Upgrades");
    }

    /** Upgrade slots: one item each; any change recomputes capacity and syncs. */
    private final class UpgradeContainer extends SimpleContainer {
        private UpgradeContainer() {
            super(UPGRADE_SLOTS);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return isValidUpgrade(this, slot, stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            recomputeUpgrades();
            contentsChanged();
        }

        /** Replaces every slot without firing change callbacks (used while loading). */
        void replaceAll(NonNullList<ItemStack> items) {
            for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
                getItems().set(slot, slot < items.size() ? items.get(slot) : ItemStack.EMPTY);
            }
            recomputeUpgrades();
        }
    }
}
