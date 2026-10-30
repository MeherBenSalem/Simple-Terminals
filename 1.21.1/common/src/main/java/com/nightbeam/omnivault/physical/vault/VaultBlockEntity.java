package com.nightbeam.omnivault.physical.vault;

import com.nightbeam.omnivault.physical.ContainerSlotAccess;
import com.nightbeam.omnivault.physical.PhysicalContent;
import com.nightbeam.omnivault.physical.StorageMenu;
import com.nightbeam.omnivault.physical.VaultLid;
import com.nightbeam.omnivault.registry.ModCapabilities;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Inventory, name and lid state of a tiered vault. The slot count comes from the block's tier. */
public class VaultBlockEntity extends BlockEntity implements Container, MenuProvider, Nameable, LidBlockEntity,
        StorageMenu.Source, ModCapabilities.ItemProvider {
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
        stack.limitSize(getMaxStackSize(stack));
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(tier.size(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        customName = tag.contains("CustomName", CompoundTag.TAG_STRING) ? parseCustomNameSafe(tag.getString("CustomName"), registries) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        customName = input.get(DataComponents.CUSTOM_NAME);
        input.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, customName);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("CustomName");
        tag.remove("Items");
    }
}
