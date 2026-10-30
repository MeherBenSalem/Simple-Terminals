package com.nightbeam.omnivault.autocraft.block;

import com.nightbeam.omnivault.autocraft.AutocraftConfig;
import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.CraftingManager;
import com.nightbeam.omnivault.autocraft.job.CraftingJob;
import com.nightbeam.omnivault.autocraft.menu.AssemblerMenu;
import com.nightbeam.omnivault.autocraft.pattern.ResolvedPattern;
import com.nightbeam.omnivault.item.UpgradeItem;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.block.MenuDevice;
import com.nightbeam.omnivault.network.block.NetworkNodeBlockEntity;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Runs crafting-pattern steps for every job on its network: a few crafts per cycle, doubled by
 * each Speed Upgrade. While it works its chamber lights up and the item it makes floats inside.
 */
public class AssemblerBlockEntity extends NetworkNodeBlockEntity implements MenuDevice {
    public static final int UPGRADE_SLOTS = 4;
    private static final int GLOW_TICKS = 30;

    private final SimpleContainer upgrades = new SimpleContainer(UPGRADE_SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem upgrade && upgrade.kind() == UpgradeItem.Kind.SPEED;
        }
    };
    private long budget;
    private long nextCycle;
    private long lastWork = Long.MIN_VALUE / 2;
    /** The last thing crafted; synced to clients for the renderer. */
    private ItemStack display = ItemStack.EMPTY;

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(AutocraftContent.ASSEMBLER_BE.get(), pos, state);
        upgrades.addListener(container -> setChanged());
    }

    public SimpleContainer upgrades() {
        return upgrades;
    }

    public int speedUpgrades() {
        int count = 0;
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            if (upgrades.getItem(slot).getItem() instanceof UpgradeItem upgrade && upgrade.kind() == UpgradeItem.Kind.SPEED) {
                count++;
            }
        }
        return Math.min(count, UpgradeItem.Kind.SPEED.maxPerDevice);
    }

    public int craftsPerCycle() {
        int base = AutocraftConfig.ASSEMBLER_CRAFTS.get() << speedUpgrades();
        return getBlockState().is(AutocraftContent.QUANTUM_ASSEMBLER.get()) ? base * QUANTUM_MULTIPLIER : base;
    }

    /** How many times faster a Quantum Assembler crafts than a plain one. */
    public static final int QUANTUM_MULTIPLIER = 16;

    public ItemStack display() {
        return display;
    }

    @Override
    public int energyUsage() {
        return 8 + 4 * speedUpgrades();
    }

    @Override
    public void setNetwork(@Nullable StorageNetwork network) {
        super.setNetwork(network);
        CraftingManager.devicesChanged();
    }

    @Override
    public void onNetworkStateChanged() {
        super.onNetworkStateChanged();
        if (!isOnline()) {
            setWorking(false);
        }
    }

    /** Called every network tick: refills the crafting budget each cycle and lets the glow fade when idle. */
    public void cycle(long gameTime) {
        if (gameTime >= nextCycle || nextCycle - gameTime > AutocraftConfig.ASSEMBLER_INTERVAL.get()) {
            budget = craftsPerCycle();
            nextCycle = gameTime + AutocraftConfig.ASSEMBLER_INTERVAL.get();
        }
        if (gameTime - lastWork > GLOW_TICKS) {
            setWorking(false);
        }
    }

    /**
     * Crafts up to {@code runs} runs of a crafting step from the job's items. Returns the runs made,
     * or -1 if the pattern's recipe no longer exists.
     */
    public long craft(CraftingJob job, CraftingJob.Step step, long runs, ServerLevel level, long gameTime) {
        long crafts = Math.min(runs, budget);
        if (crafts <= 0) {
            return 0;
        }
        ResolvedPattern.CraftResult result = step.pattern().craft(level);
        if (result == null) {
            return -1;
        }
        job.consumeInputs(step, crafts);
        job.add(result.result(), result.count() * crafts);
        for (ResolvedPattern.KeyAmount remainder : result.remainders()) {
            job.add(remainder.key(), remainder.amount() * crafts);
        }
        step.ran(crafts);
        budget -= crafts;
        lastWork = gameTime;
        setWorking(true);
        ItemStack made = result.result().toStack();
        if (!ItemStack.isSameItemSameComponents(made, display)) {
            display = made;
            sync();
        }
        return crafts;
    }

    private void setWorking(boolean working) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(AutocraftDeviceBlock.WORKING) && state.getValue(AutocraftDeviceBlock.WORKING) != working) {
            level.setBlock(worldPosition, state.setValue(AutocraftDeviceBlock.WORKING, working), 3);
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider(
                (id, inventory, p) -> new AssemblerMenu(id, inventory, this), getBlockState().getBlock().getName()),
                buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, upgrades);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("upgrades", ContainerHelper.saveAllItems(new CompoundTag(), upgrades.getItems(), registries));
        if (!display.isEmpty()) {
            tag.put("display", display.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> loaded = NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("upgrades"), loaded, registries);
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            upgrades.getItems().set(slot, loaded.get(slot));
        }
        display = tag.contains("display") ? ItemStack.parse(registries, tag.getCompound("display")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (!display.isEmpty()) {
            tag.put("display", display.save(registries));
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
