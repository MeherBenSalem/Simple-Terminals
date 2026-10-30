package com.nightbeam.omnivault.autocraft.block;

import com.nightbeam.omnivault.autocraft.AutocraftConfig;
import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.CraftingManager;
import com.nightbeam.omnivault.autocraft.job.CraftingJob;
import com.nightbeam.omnivault.autocraft.menu.PatternProviderMenu;
import com.nightbeam.omnivault.autocraft.pattern.PatternData;
import com.nightbeam.omnivault.autocraft.pattern.PatternItem;
import com.nightbeam.omnivault.autocraft.pattern.ResolvedPattern;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.block.DirectionalDeviceBlock;
import com.nightbeam.omnivault.network.block.MenuDevice;
import com.nightbeam.omnivault.network.block.NetworkNodeBlockEntity;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import com.nightbeam.omnivault.storage.SlotAccess;
import com.nightbeam.omnivault.storage.SlotAccessStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * Holds nine encoded patterns and offers them to the network. For a processing pattern it hands
 * the inputs of each run to the inventory it faces (a furnace, any mod's machine); the results come
 * back through any network insert (an import bus, a network port) or through this block's own
 * inventory, which machines and pipes can push into. Crafting patterns are run by Assemblers.
 */
public class PatternProviderBlockEntity extends NetworkNodeBlockEntity implements MenuDevice, ModCapabilities.ItemProvider {
    public static final int SLOTS = 9;

    private final SimpleContainer patterns = new SimpleContainer(SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return PatternItem.isEncoded(stack);
        }
    };
    /** Inputs that were promised to the machine but did not fit; pushed before anything else. */
    private final List<ItemStack> overflow = new ArrayList<>();
    private final ReturnSlot returnSlot = new ReturnSlot();
    @Nullable
    private List<ResolvedPattern> resolved;

    public PatternProviderBlockEntity(BlockPos pos, BlockState state) {
        super(AutocraftContent.PATTERN_PROVIDER_BE.get(), pos, state);
        patterns.addListener(container -> onPatternsChanged());
    }

    public SimpleContainer patterns() {
        return patterns;
    }

    private void onPatternsChanged() {
        resolved = null;
        setChanged();
        CraftingManager.devicesChanged();
    }

    /** The valid patterns this provider holds, in slot order. */
    public List<ResolvedPattern> resolvedPatterns() {
        if (resolved == null) {
            List<ResolvedPattern> list = new ArrayList<>();
            for (int slot = 0; slot < SLOTS; slot++) {
                PatternData data = PatternItem.data(patterns.getItem(slot));
                if (data != null) {
                    list.add(new ResolvedPattern(data));
                }
            }
            resolved = List.copyOf(list);
        }
        return resolved;
    }

    @Override
    public void setNetwork(@Nullable StorageNetwork network) {
        super.setNetwork(network);
        CraftingManager.devicesChanged();
    }

    @Override
    public int energyUsage() {
        return 2;
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(DirectionalDeviceBlock.FACING) ? state.getValue(DirectionalDeviceBlock.FACING) : Direction.NORTH;
    }

    @Nullable
    private SlotAccess target() {
        if (level == null) {
            return null;
        }
        Direction facing = facing();
        return Services.PLATFORM.findSlotAccess(level, worldPosition.relative(facing), facing.getOpposite()).orElse(null);
    }

    /** Whether earlier inputs are still waiting to fit into the machine. */
    public boolean blocked() {
        return !overflow.isEmpty();
    }

    /**
     * Hands up to {@code runs} runs of a processing step to the machine in front, taking the
     * inputs from the job. Returns how many runs went out.
     */
    public long push(CraftingJob job, CraftingJob.Step step, long runs) {
        if (!overflow.isEmpty()) {
            flushOverflow();
            if (!overflow.isEmpty()) {
                return 0;
            }
        }
        SlotAccess target = target();
        if (target == null) {
            return 0;
        }
        SlotAccessStorage machine = new SlotAccessStorage(target);
        long limit = Math.min(runs, AutocraftConfig.PROVIDER_PUSHES.get());
        long pushed = 0;
        while (pushed < limit && overflow.isEmpty()) {
            for (ResolvedPattern.KeyAmount input : step.pattern().inputs()) {
                if (machine.insert(input.key(), input.amount(), true) < input.amount()) {
                    return finish(pushed);
                }
            }
            for (ResolvedPattern.KeyAmount input : step.pattern().inputs()) {
                long placed = machine.insert(input.key(), input.amount(), false);
                if (placed < input.amount()) {
                    // The machine accepted less than it promised (inputs competing for one slot): keep the rest.
                    addOverflow(input.key(), input.amount() - placed);
                }
            }
            job.consumeInputs(step, 1);
            job.dispatched(step, 1);
            pushed++;
        }
        return finish(pushed);
    }

    private long finish(long pushed) {
        if (pushed > 0) {
            setChanged();
        }
        return pushed;
    }

    private void addOverflow(ItemKey key, long amount) {
        while (amount > 0) {
            int count = (int) Math.min(amount, key.maxStackSize());
            overflow.add(key.toStack(count));
            amount -= count;
        }
    }

    /** Retries inputs that did not fit last time. */
    public void flushOverflow() {
        if (overflow.isEmpty()) {
            return;
        }
        SlotAccess target = target();
        if (target == null) {
            return;
        }
        SlotAccessStorage machine = new SlotAccessStorage(target);
        for (int i = 0; i < overflow.size(); i++) {
            ItemStack stack = overflow.get(i);
            ItemKey key = ItemKey.of(stack);
            long placed = key == null ? stack.getCount() : machine.insert(key, stack.getCount(), false);
            stack.shrink((int) placed);
        }
        overflow.removeIf(ItemStack::isEmpty);
        setChanged();
    }

    // -- inventory exposed to machines -------------------------------------------------------------

    @Nullable
    @Override
    public SlotAccess getSlotAccess(@Nullable Direction side) {
        return returnSlot;
    }

    /**
     * One insert-only slot: whatever a machine pushes back goes to the network, where crafting jobs
     * waiting for it collect it first.
     */
    private final class ReturnSlot implements SlotAccess {
        private int depth;

        /** Insert-only, like the slot: the network takes what machines push back. */
        private final ItemStorage whole = new ItemStorage() {
            @Override
            public long insert(ItemKey key, long amount, boolean simulate) {
                StorageNetwork network = getNetwork();
                if (network == null || !network.isActive() || depth > 0) {
                    return 0;
                }
                depth++;
                try {
                    return network.storage().insert(key, amount, simulate);
                } finally {
                    depth--;
                }
            }

            @Override
            public long extract(ItemKey key, long amount, boolean simulate) {
                return 0;
            }

            @Override
            public void forEach(ObjLongConsumer<ItemKey> consumer) {
            }
        };

        @Override
        public ItemStorage networkStorage() {
            return whole;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            StorageNetwork network = getNetwork();
            ItemKey key = ItemKey.of(stack);
            // The depth guard stops loops through storage buses that point back at this provider.
            if (network == null || !network.isActive() || key == null || depth > 0) {
                return stack;
            }
            depth++;
            try {
                long inserted = network.storage().insert(key, stack.getCount(), simulate);
                return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount((int) (stack.getCount() - inserted));
            } finally {
                depth--;
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
    }

    // -- menu, drops, saving -----------------------------------------------------------------------

    @Override
    public void openMenu(ServerPlayer player) {
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider(
                (id, inventory, p) -> new PatternProviderMenu(id, inventory, this), getBlockState().getBlock().getName()),
                buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, patterns);
        for (ItemStack stack : overflow) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        overflow.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("patterns", ContainerHelper.saveAllItems(new CompoundTag(), patterns.getItems(), registries));
        ListTag pending = new ListTag();
        for (ItemStack stack : overflow) {
            pending.add(stack.save(registries));
        }
        tag.put("overflow", pending);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> loaded = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("patterns"), loaded, registries);
        for (int slot = 0; slot < SLOTS; slot++) {
            patterns.getItems().set(slot, loaded.get(slot));
        }
        overflow.clear();
        ListTag pending = tag.getList("overflow", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            ItemStack.parse(registries, pending.getCompound(i)).ifPresent(overflow::add);
        }
        resolved = null;
    }
}
