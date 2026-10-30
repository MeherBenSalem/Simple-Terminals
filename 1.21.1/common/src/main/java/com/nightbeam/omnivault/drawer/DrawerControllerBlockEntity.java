package com.nightbeam.omnivault.drawer;

import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.ObjLongConsumer;

/**
 * Connects every drawer touching it, directly or through other drawers and trims, within the
 * configured range. Inserts go to drawers that already hold (or are locked to) the item first, then to
 * empty ones. Seen as an inventory: slot 0 accepts anything and routes it, slots 1.. are the
 * compartments of every connected drawer.
 */
public class DrawerControllerBlockEntity extends BlockEntity implements ModCapabilities.ItemProvider {
    private static final int RESCAN_TICKS = 40;

    private final ControllerAccess access = new ControllerAccess();
    private List<AbstractDrawerBlockEntity> drawers = List.of();
    private int[] slotDrawer = new int[0];
    private int[] slotCompartment = new int[0];
    private long lastScan;
    private boolean dirty = true;
    private long lastClickTick = Long.MIN_VALUE / 2;
    @Nullable
    private UUID lastClickPlayer;

    public DrawerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(DrawerContent.CONTROLLER_BE.get(), pos, state);
    }

    /** Forces a rescan on the next access, e.g. when a neighbour changes. */
    public void invalidate() {
        dirty = true;
    }

    /** The connected drawers, rescanned every couple of seconds or when one was removed. */
    public List<AbstractDrawerBlockEntity> drawers() {
        if (level == null) {
            return List.of();
        }
        long now = level.getGameTime();
        boolean stale = dirty || now - lastScan >= RESCAN_TICKS || now < lastScan;
        if (!stale) {
            for (AbstractDrawerBlockEntity drawer : drawers) {
                if (drawer.isRemoved()) {
                    stale = true;
                    break;
                }
            }
        }
        if (stale) {
            scan();
            lastScan = now;
            dirty = false;
        }
        return drawers;
    }

    private void scan() {
        int range = DrawerConfig.CONTROLLER_RANGE.get();
        int max = DrawerConfig.CONTROLLER_MAX_BLOCKS.get();
        BlockPos origin = getBlockPos();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<AbstractDrawerBlockEntity> found = new ArrayList<>();
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && visited.size() <= max) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (visited.contains(next) || Math.abs(next.getX() - origin.getX()) > range
                        || Math.abs(next.getY() - origin.getY()) > range || Math.abs(next.getZ() - origin.getZ()) > range
                        || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof DrawerBlock) {
                    visited.add(next);
                    queue.add(next);
                    if (level.getBlockEntity(next) instanceof AbstractDrawerBlockEntity drawer) {
                        found.add(drawer);
                    }
                } else if (state.getBlock() instanceof DrawerTrimBlock) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        drawers = Collections.unmodifiableList(found);
        int slots = 0;
        for (AbstractDrawerBlockEntity drawer : found) {
            slots += drawer.compartments();
        }
        slotDrawer = new int[slots];
        slotCompartment = new int[slots];
        int index = 0;
        for (int d = 0; d < found.size(); d++) {
            for (int c = 0; c < found.get(d).compartments(); c++) {
                slotDrawer[index] = d;
                slotCompartment[index] = c;
                index++;
            }
        }
    }

    /**
     * Routes items into the connected drawers: first every drawer that holds or is locked to the type,
     * then the first drawer with a free compartment. @return how many were (or would be) taken.
     */
    public long insert(ItemKey key, long amount, boolean simulate) {
        long remaining = amount;
        List<AbstractDrawerBlockEntity> all = drawers();
        for (AbstractDrawerBlockEntity drawer : all) {
            if (remaining <= 0) {
                break;
            }
            if (!drawer.isRemoved() && drawer.holds(key)) {
                remaining -= drawer.insertAny(key, remaining, simulate);
            }
        }
        if (remaining > 0) {
            for (AbstractDrawerBlockEntity drawer : all) {
                if (remaining <= 0) {
                    break;
                }
                if (!drawer.isRemoved() && !drawer.holds(key)) {
                    remaining -= drawer.insertAny(key, remaining, simulate);
                }
            }
        }
        return amount - remaining;
    }

    public long extract(ItemKey key, long amount, boolean simulate) {
        long remaining = amount;
        for (AbstractDrawerBlockEntity drawer : drawers()) {
            if (remaining <= 0) {
                break;
            }
            if (!drawer.isRemoved() && drawer.holds(key)) {
                remaining -= drawer.access().extract(key, remaining, simulate);
            }
        }
        return amount - remaining;
    }

    /** True if any connected drawer holds or is locked to this type. */
    public boolean holds(ItemKey key) {
        for (AbstractDrawerBlockEntity drawer : drawers()) {
            if (!drawer.isRemoved() && drawer.holds(key)) {
                return true;
            }
        }
        return false;
    }

    /** Locks every connected drawer if any is unlocked, otherwise unlocks them all. @return the new state. */
    public boolean toggleLockAll() {
        boolean lock = false;
        for (AbstractDrawerBlockEntity drawer : drawers()) {
            if (!drawer.isLocked()) {
                lock = true;
                break;
            }
        }
        for (AbstractDrawerBlockEntity drawer : drawers()) {
            drawer.setLocked(lock);
        }
        return lock;
    }

    /** Inserts the held stack, routed. @return items taken. */
    public long insertHeld(Player player, ItemStack held) {
        if (held.isEmpty()) {
            return 0;
        }
        long accepted = insert(ItemKey.of(held), held.getCount(), false);
        held.shrink((int) Math.min(accepted, held.getCount()));
        return accepted;
    }

    /** Moves every inventory stack some connected drawer already holds. @return items taken. */
    public long insertInventory(Player player) {
        Inventory inventory = player.getInventory();
        long total = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemKey key = ItemKey.of(stack);
            if (!holds(key)) {
                continue;
            }
            long accepted = insert(key, stack.getCount(), false);
            stack.shrink((int) Math.min(accepted, stack.getCount()));
            total += accepted;
        }
        if (total > 0) {
            inventory.setChanged();
        }
        return total;
    }

    public boolean registerClick(Player player, long gameTime) {
        boolean second = player.getUUID().equals(lastClickPlayer) && gameTime - lastClickTick <= DrawerConfig.DOUBLE_CLICK_TICKS.get();
        lastClickTick = second ? Long.MIN_VALUE / 2 : gameTime;
        lastClickPlayer = player.getUUID();
        return second;
    }

    @Override
    @Nullable
    public SlotAccess getSlotAccess(@Nullable Direction side) {
        return access;
    }

    public ControllerAccess access() {
        return access;
    }

    /** Slot 0 routes inserts; slots 1.. are the connected compartments. Also counted storage for the network. */
    public final class ControllerAccess implements SlotAccess, ItemStorage {
        private ControllerAccess() {
        }

        @Nullable
        private AbstractDrawerBlockEntity drawerFor(int slot) {
            int index = slot - 1;
            if (index < 0 || index >= slotDrawer.length) {
                return null;
            }
            AbstractDrawerBlockEntity drawer = drawers.get(slotDrawer[index]);
            return drawer.isRemoved() ? null : drawer;
        }

        @Override
        public int getSlots() {
            drawers();
            return 1 + slotDrawer.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            AbstractDrawerBlockEntity drawer = drawerFor(slot);
            return drawer == null ? ItemStack.EMPTY : drawer.access().getStackInSlot(slotCompartment[slot - 1]);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return stack;
            }
            if (slot == 0) {
                long accepted = insert(ItemKey.of(stack), stack.getCount(), simulate);
                if (accepted >= stack.getCount()) {
                    return ItemStack.EMPTY;
                }
                return accepted <= 0 ? stack : stack.copyWithCount(stack.getCount() - (int) accepted);
            }
            AbstractDrawerBlockEntity drawer = drawerFor(slot);
            return drawer == null ? stack : drawer.access().insertItem(slotCompartment[slot - 1], stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            AbstractDrawerBlockEntity drawer = drawerFor(slot);
            return drawer == null ? ItemStack.EMPTY : drawer.access().extractItem(slotCompartment[slot - 1], amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == 0) {
                return Integer.MAX_VALUE;
            }
            AbstractDrawerBlockEntity drawer = drawerFor(slot);
            return drawer == null ? 0 : drawer.access().getSlotLimit(slotCompartment[slot - 1]);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == 0) {
                return !stack.isEmpty() && insert(ItemKey.of(stack), 1, true) > 0;
            }
            AbstractDrawerBlockEntity drawer = drawerFor(slot);
            return drawer != null && drawer.access().isItemValid(slotCompartment[slot - 1], stack);
        }

        @Override
        public long insert(ItemKey key, long amount, boolean simulate) {
            return DrawerControllerBlockEntity.this.insert(key, amount, simulate);
        }

        @Override
        public long extract(ItemKey key, long amount, boolean simulate) {
            return DrawerControllerBlockEntity.this.extract(key, amount, simulate);
        }

        @Override
        public void forEach(ObjLongConsumer<ItemKey> consumer) {
            for (AbstractDrawerBlockEntity drawer : drawers()) {
                if (!drawer.isRemoved()) {
                    drawer.access().forEach(consumer);
                }
            }
        }

        @Override
        public boolean isPreferredFor(ItemKey key) {
            return holds(key);
        }
    }
}
