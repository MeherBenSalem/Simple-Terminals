package com.nightbeam.omnivault.backpack;

import com.nightbeam.omnivault.backpack.upgrade.BackpackUpgradeItem;
import com.nightbeam.omnivault.backpack.upgrade.UpgradeConfig;
import com.nightbeam.omnivault.backpack.upgrade.UpgradeType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The contents and installed upgrades of one backpack, identified by the UUID on its item. */
public final class BackpackData {
    private final BackpackInventory items;
    private final BackpackInventory upgrades;
    private final Runnable markDirty;
    private long version;
    /** Content version the compacting upgrade last ran at (not saved). */
    private long compactedVersion = -1;

    BackpackData(int size, int upgradeSlots, Runnable markDirty) {
        this.markDirty = markDirty;
        this.items = new BackpackInventory(size, Backpacks::canStore, 99, this::changed);
        this.upgrades = new BackpackInventory(upgradeSlots, stack -> stack.getItem() instanceof BackpackUpgradeItem, 1, this::changed);
    }

    private void changed() {
        version++;
        markDirty.run();
    }

    public BackpackInventory items() {
        return items;
    }

    public BackpackInventory upgrades() {
        return upgrades;
    }

    /** Increases whenever the contents or upgrades change. */
    public long version() {
        return version;
    }

    public long compactedVersion() {
        return compactedVersion;
    }

    /** Records that compacting has handled the current contents. */
    public void markCompacted() {
        compactedVersion = version;
    }

    /** Grows the inventories to what {@code tier} offers (an upgraded backpack keeps all its items). */
    public void ensureCapacity(BackpackTier tier) {
        items.ensureSize(tier.slots());
        upgrades.ensureSize(tier.upgradeSlots());
    }

    /** The first installed, enabled upgrade card of {@code type}, or null. */
    @Nullable
    public ItemStack upgrade(UpgradeType type) {
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            ItemStack stack = upgrades.getItem(i);
            if (stack.getItem() instanceof BackpackUpgradeItem item && item.type() == type && BackpackUpgradeItem.config(stack).enabled()) {
                return stack;
            }
        }
        return null;
    }

    public boolean hasUpgrade(UpgradeType type) {
        return upgrade(type) != null;
    }

    @Nullable
    public UpgradeConfig config(UpgradeType type) {
        ItemStack stack = upgrade(type);
        return stack == null ? null : BackpackUpgradeItem.config(stack);
    }

    /** Every installed upgrade card (enabled or not), in slot order. */
    public List<ItemStack> installedUpgrades() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (!upgrades.getItem(i).isEmpty()) {
                list.add(upgrades.getItem(i));
            }
        }
        return list;
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Items", items.save(registries));
        tag.put("Upgrades", upgrades.save(registries));
        return tag;
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        items.load(tag.getCompound("Items"), registries);
        upgrades.load(tag.getCompound("Upgrades"), registries);
    }
}
