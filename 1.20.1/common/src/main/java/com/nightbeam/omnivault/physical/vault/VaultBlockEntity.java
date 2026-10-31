package com.nightbeam.omnivault.physical.vault;

import com.nightbeam.omnivault.physical.ContainerSlotAccess;
import com.nightbeam.omnivault.physical.PhysicalContent;
import com.nightbeam.omnivault.physical.StorageMenu;
import com.nightbeam.omnivault.physical.VaultLid;
import com.nightbeam.omnivault.registry.ImplicitComponents;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Inventory, name and lid state of a tiered vault. The slot count comes from the block's tier. */
public class VaultBlockEntity extends BlockEntity implements Container, MenuProvider, Nameable, LidBlockEntity,
        StorageMenu.Source, ModCapabilities.ItemProvider, ImplicitComponents {
    private final VaultTier tier;
    private final VaultLid lid;
    private NonNullList<ItemStack> items;
    @Nullable
    private Component customName;

    public VaultBlockEntity(BlockPos pos, BlockState state) {
        super(PhysicalContent.VAULT_BE.get(), pos, state);
        this.tier = state.getBlock() instanceof VaultBlock vault ? vault.tier() : VaultTier.COPPER;
        this.items = NonNullList.withSize(tier.size(), ItemStack.EMPTY);
        this.lid = new VaultLid(this, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, tier.lidPitch());
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, VaultBlockEntity vault) {
        vault.lid.tick();
    }

    public VaultTier tier() {
        return tier;
    }

    public VaultLid lid() {
        return lid;
    }

    @Override
    public float getOpenNess(float partialTick) {
        return lid.openness(partialTick);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return lid.onBlockEvent(id, param) || super.triggerEvent(id, param);
    }

    // -- Container ---------------------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        int limit = Math.min(getMaxStackSize(), stack.getMaxStackSize());
        if (stack.getCount() > limit) {
            stack.setCount(limit);
        }
        setChanged();
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void startOpen(Player player) {
        lid.open(player);
    }

    @Override
    public void stopOpen(Player player) {
        lid.close(player);
    }

    // -- Naming and menus ------------------------------------------------------------------------------

    @Override
    public Component getName() {
        return customName != null ? customName : Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return customName;
    }

    public void setCustomName(@Nullable Component name) {
        this.customName = name;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new StorageMenu(PhysicalContent.VAULT_MENU.get(), containerId, inventory, this, tier.layout(), this, tier, null);
    }

    @Nullable
    @Override
    public SlotAccess getSlotAccess(@Nullable Direction side) {
        return ContainerSlotAccess.of(this);
    }

    // -- Persistence ------------------------------------------------------------------------------------

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(tier.size(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        customName = tag.contains("CustomName", CompoundTag.TAG_STRING) ? Component.Serializer.fromJson(tag.getString("CustomName")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName));
        }
    }

    /** The items themselves come from the item's {@code BlockEntityTag}, which vanilla applies on placement. */
    @Override
    public void applyImplicitComponents(ItemStack stack) {
        customName = stack.hasCustomHoverName() ? stack.getHoverName() : null;
    }

    @Override
    public void collectImplicitComponents(ItemStack stack) {
        if (customName != null) {
            stack.setHoverName(customName);
        }
        CompoundTag data = new CompoundTag();
        ContainerHelper.saveAllItems(data, items, false);
        if (!data.isEmpty()) {
            BlockItem.setBlockEntityData(stack, getType(), data);
        }
    }
}
