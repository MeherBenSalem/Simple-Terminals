package com.nightbeam.omnivault.autocraft.block;

import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.menu.PatternTerminalMenu;
import com.nightbeam.omnivault.autocraft.pattern.PatternData;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.block.MenuDevice;
import com.nightbeam.omnivault.network.block.NetworkNodeBlockEntity;
import com.nightbeam.omnivault.network.menu.TerminalHost;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Pattern Encoding Terminal: a network terminal plus a pattern workbench. The ghost recipe, the
 * mode and the two pattern slots stay with the block.
 */
public class PatternTerminalBlockEntity extends NetworkNodeBlockEntity implements MenuDevice, TerminalHost, PatternTerminalMenu.Workbench {
    private final SimpleContainer inputs = new SimpleContainer(PatternData.MAX_INPUTS);
    private final SimpleContainer outputs = new SimpleContainer(PatternData.MAX_OUTPUTS);
    /** Slot 0: blank patterns in. Slot 1: the encoded pattern out. */
    private final SimpleContainer patterns = new SimpleContainer(2);
    private boolean processing;

    public PatternTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(AutocraftContent.PATTERN_TERMINAL_BE.get(), pos, state);
        inputs.addListener(c -> setChanged());
        outputs.addListener(c -> setChanged());
        patterns.addListener(c -> setChanged());
    }

    @Override
    public SimpleContainer inputs() {
        return inputs;
    }

    @Override
    public SimpleContainer outputs() {
        return outputs;
    }

    @Override
    public SimpleContainer patterns() {
        return patterns;
    }

    @Override
    public boolean processing() {
        return processing;
    }

    @Override
    public void setProcessing(boolean processing) {
        this.processing = processing;
        setChanged();
    }

    @Override
    public int energyUsage() {
        return 5;
    }

    @Nullable
    @Override
    public StorageNetwork network() {
        return getNetwork();
    }

    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }

    @Override
    public void openMenu(ServerPlayer player) {
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider((id, inventory, p) -> new PatternTerminalMenu(id, inventory, this),
                getBlockState().getBlock().getName()), buf -> buf.writeBoolean(processing));
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, patterns);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inputs", ContainerHelper.saveAllItems(new CompoundTag(), inputs.getItems(), registries));
        tag.put("outputs", ContainerHelper.saveAllItems(new CompoundTag(), outputs.getItems(), registries));
        tag.put("patterns", ContainerHelper.saveAllItems(new CompoundTag(), patterns.getItems(), registries));
        tag.putBoolean("processing", processing);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        load(tag.getCompound("inputs"), inputs, registries);
        load(tag.getCompound("outputs"), outputs, registries);
        load(tag.getCompound("patterns"), patterns, registries);
        processing = tag.getBoolean("processing");
    }

    private static void load(CompoundTag tag, SimpleContainer container, HolderLookup.Provider registries) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int slot = 0; slot < items.size(); slot++) {
            container.getItems().set(slot, items.get(slot));
        }
    }
}
