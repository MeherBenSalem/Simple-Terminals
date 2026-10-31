package com.nightbeam.omnivault.autocraft.block;

import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.menu.PatternTerminalMenu;
import com.nightbeam.omnivault.autocraft.pattern.PatternData;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.block.MenuDevice;
import com.nightbeam.omnivault.network.block.NetworkNodeBlockEntity;
import com.nightbeam.omnivault.network.menu.TerminalHost;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.util.ObservedContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Pattern Encoding Terminal: a network terminal plus a pattern workbench. The ghost recipe, the
 * mode and the two pattern slots stay with the block.
 */
public class PatternTerminalBlockEntity extends NetworkNodeBlockEntity implements MenuDevice, TerminalHost, PatternTerminalMenu.Workbench {
    private final ObservedContainer inputs = new ObservedContainer(PatternData.MAX_INPUTS);
    private final ObservedContainer outputs = new ObservedContainer(PatternData.MAX_OUTPUTS);
    /** Slot 0: blank patterns in. Slot 1: the encoded pattern out. */
    private final ObservedContainer patterns = new ObservedContainer(2);
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
        return !isRemoved() && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64;
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output.child("inputs"), inputs.getItems());
        ContainerHelper.saveAllItems(output.child("outputs"), outputs.getItems());
        ContainerHelper.saveAllItems(output.child("patterns"), patterns.getItems());
        output.putBoolean("processing", processing);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        load(input.childOrEmpty("inputs"), inputs);
        load(input.childOrEmpty("outputs"), outputs);
        load(input.childOrEmpty("patterns"), patterns);
        processing = input.getBooleanOr("processing", false);
    }

    private static void load(ValueInput input, SimpleContainer container) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        for (int slot = 0; slot < items.size(); slot++) {
            container.getItems().set(slot, items.get(slot));
        }
    }
}
