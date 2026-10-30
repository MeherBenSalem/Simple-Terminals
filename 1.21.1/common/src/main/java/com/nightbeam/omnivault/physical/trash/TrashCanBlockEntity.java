package com.nightbeam.omnivault.physical.trash;

import com.nightbeam.omnivault.physical.PhysicalContent;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TrashCanBlockEntity extends BlockEntity implements MenuProvider, ModCapabilities.ItemProvider {
    private final TrashInventory inventory = new TrashInventory(this::setChanged);

    public TrashCanBlockEntity(BlockPos pos, BlockState state) {
        super(PhysicalContent.TRASH_BE.get(), pos, state);
    }

    public TrashInventory inventory() {
        return inventory;
    }

    public boolean stillValid(Player player) {
        return !isRemoved() && Container.stillValidBlockEntity(this, player);
    }

    @Nullable
    @Override
    public SlotAccess getSlotAccess(@Nullable Direction side) {
        return inventory.slotAccess;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TrashMenu(containerId, playerInventory, inventory, this::stillValid, worldPosition);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.load(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        inventory.save(tag, registries);
    }
}
