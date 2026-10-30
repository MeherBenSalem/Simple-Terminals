package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.menu.CraftingTerminalMenu;
import com.nightbeam.omnivault.network.menu.TerminalHost;
import com.nightbeam.omnivault.network.menu.TerminalMenu;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TerminalBlockEntity extends NetworkNodeBlockEntity implements MenuDevice, TerminalHost {
    private final NonNullList<ItemStack> craftingGrid = NonNullList.withSize(9, ItemStack.EMPTY);

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.TERMINAL_BE.get(), pos, state);
    }

    public TerminalBlock.Kind kind() {
        return getBlockState().getBlock() instanceof TerminalBlock terminal ? terminal.kind() : TerminalBlock.Kind.STORAGE;
    }

    @Override
    public int energyUsage() {
        return kind() == TerminalBlock.Kind.CRAFTING ? 6 : 4;
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

    @Nullable
    @Override
    public NonNullList<ItemStack> persistentCraftingGrid() {
        return kind() == TerminalBlock.Kind.CRAFTING ? craftingGrid : null;
    }

    @Override
    public void openMenu(ServerPlayer player) {
        boolean crafting = kind() == TerminalBlock.Kind.CRAFTING;
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider((id, inventory, p) -> crafting
                ? new CraftingTerminalMenu(id, inventory, this)
                : new TerminalMenu(NetworkContent.TERMINAL_MENU.get(), id, inventory, this),
                getBlockState().getBlock().getName()), buf -> {
        });
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : craftingGrid) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        craftingGrid.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, craftingGrid, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        craftingGrid.clear();
        ContainerHelper.loadAllItems(tag, craftingGrid, registries);
    }
}
