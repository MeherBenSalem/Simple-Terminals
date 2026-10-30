package com.nightbeam.omnivault.drawer;

import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Three linked compartments for an item and its compressed forms (most compressed on top). The count
 * is stored once, in base items, and every tier shows how many of itself that amount makes.
 */
public class CompactingDrawerBlockEntity extends AbstractDrawerBlockEntity {
    public static final int TIERS = 3;

    private final ItemKey[] keys = new ItemKey[TIERS];
    private final int[] units = new int[TIERS];
    private int tiers;
    private long amount;

    public CompactingDrawerBlockEntity(BlockPos pos, BlockState state) {
        super(DrawerContent.COMPACTING_BE.get(), pos, state);
    }

    @Override
    public int compartments() {
        return TIERS;
    }

    /** How many tiers the current chain has (0 when no item is set). */
    public int tiers() {
        return tiers;
    }

    /** How many base items one item of {@code slot}'s tier is worth. */
    public int unitAt(int slot) {
        return slot < tiers ? units[slot] : 0;
    }

    /** The stored amount in base items. */
    public long amount() {
        return amount;
    }

    @Override
    @Nullable
    public ItemKey keyAt(int slot) {
        return slot < tiers ? keys[slot] : null;
    }

    @Override
    public long countAt(int slot) {
        return slot < tiers ? amount / units[slot] : 0;
    }

    @Override
    public long capacityAt(int slot, int multiplier) {
        if (tiers == 0) {
            return slot == 0 ? (long) layout().baseStacks() * multiplier * 64 : 0;
        }
        return slot < tiers ? baseCapacity(multiplier) / units[slot] : 0;
    }

    /** Capacity in base items: the configured stacks of the most compressed tier. */
    private long baseCapacity(int multiplier) {
        return (long) layout().baseStacks() * multiplier * keys[0].maxStackSize() * units[0];
    }

    private int tierOf(ItemKey key) {
        for (int i = 0; i < tiers; i++) {
            if (keys[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean accepts(int slot, ItemKey key) {
        return tiers == 0 || tierOf(key) == slot;
    }

    @Override
    public long insertAny(ItemKey key, long amount, boolean simulate) {
        int tier = tiers == 0 ? 0 : tierOf(key);
        return tier < 0 ? 0 : insert(tier, key, amount, simulate);
    }

    @Override
    public long insert(int slot, ItemKey key, long count, boolean simulate) {
        if (count <= 0) {
            return 0;
        }
        ItemKey[] chainKeys = keys;
        int[] chainUnits = units;
        int chainSize = tiers;
        if (tiers == 0) {
            if (level == null) {
                return 0;
            }
            CompactingRules.Chain chain = CompactingRules.chainFor(level, key);
            chainSize = chain.size();
            chainKeys = chain.keys().toArray(new ItemKey[0]);
            chainUnits = chain.units();
        }
        int tier = indexOf(chainKeys, chainSize, key);
        if (tier < 0 || (tiers > 0 && tier != slot)) {
            return 0;
        }
        long capacity = (long) layout().baseStacks() * capacityMultiplier() * chainKeys[0].maxStackSize() * chainUnits[0];
        long unit = chainUnits[tier];
        long accepted = Math.min(count, Math.max(0, capacity - amount) / unit);
        if (!simulate && accepted > 0) {
            if (tiers == 0) {
                setChain(chainKeys, chainUnits, chainSize);
            }
            amount += accepted * unit;
            contentsChanged();
        }
        return isVoiding() ? count : accepted;
    }

    private static int indexOf(ItemKey[] keys, int size, ItemKey key) {
        for (int i = 0; i < size; i++) {
            if (keys[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private void setChain(ItemKey[] chainKeys, int[] chainUnits, int size) {
        tiers = Math.min(TIERS, size);
        for (int i = 0; i < TIERS; i++) {
            keys[i] = i < tiers ? chainKeys[i] : null;
            units[i] = i < tiers ? chainUnits[i] : 0;
        }
    }

    @Override
    public long extract(int slot, long count, boolean simulate) {
        if (count <= 0 || slot >= tiers) {
            return 0;
        }
        long taken = Math.min(count, amount / units[slot]);
        if (!simulate && taken > 0) {
            amount -= taken * units[slot];
            if (amount <= 0 && !isLocked()) {
                setChain(new ItemKey[0], new int[0], 0);
            }
            contentsChanged();
        }
        return taken;
    }

    @Override
    protected void forgetEmptyTypes() {
        if (amount <= 0) {
            setChain(new ItemKey[0], new int[0], 0);
        }
    }

    @Override
    public boolean isEmpty() {
        return amount <= 0;
    }

    @Override
    public double fillFraction() {
        return tiers == 0 ? 0 : (double) amount / Math.max(1, baseCapacity(capacityMultiplier()));
    }

    // ---------------------------------------------------------------------------------------------

    public CompactingContents toContents() {
        List<CompactingContents.Tier> list = new ArrayList<>(tiers);
        for (int i = 0; i < tiers; i++) {
            list.add(new CompactingContents.Tier(keys[i], units[i]));
        }
        return new CompactingContents(list, amount);
    }

    private void fromContents(CompactingContents contents) {
        List<CompactingContents.Tier> list = contents.tiers();
        int size = Math.min(TIERS, list.size());
        ItemKey[] chainKeys = new ItemKey[size];
        int[] chainUnits = new int[size];
        for (int i = 0; i < size; i++) {
            chainKeys[i] = list.get(i).key();
            chainUnits[i] = Math.max(1, list.get(i).unit());
        }
        setChain(chainKeys, chainUnits, size);
        amount = size == 0 ? 0 : Math.max(0, contents.amount());
    }

    @Override
    protected void saveContents(CompoundTag tag, HolderLookup.Provider registries) {
        if (tiers == 0) {
            return;
        }
        ListTag list = new ListTag();
        for (int i = 0; i < tiers; i++) {
            CompoundTag entry = new CompoundTag();
            entry.put("Item", keys[i].save(registries));
            entry.putInt("Unit", units[i]);
            list.add(entry);
        }
        tag.put("Tiers", list);
        tag.putLong("Amount", amount);
    }

    @Override
    protected void loadContents(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = tag.getList("Tiers", Tag.TAG_COMPOUND);
        List<ItemKey> chainKeys = new ArrayList<>();
        List<Integer> chainUnits = new ArrayList<>();
        for (int i = 0; i < Math.min(TIERS, list.size()); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemKey key = ItemKey.load(registries, entry.get("Item"));
            if (key == null) {
                break; // an item that no longer exists: keep the chain above it
            }
            chainKeys.add(key);
            chainUnits.add(Math.max(1, entry.getInt("Unit")));
        }
        int[] unitArray = chainUnits.stream().mapToInt(Integer::intValue).toArray();
        setChain(chainKeys.toArray(new ItemKey[0]), unitArray, chainKeys.size());
        amount = tiers == 0 ? 0 : Math.max(0, tag.getLong("Amount"));
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        CompactingContents contents = input.get(DrawerContent.COMPACTING_CONTENTS.get());
        if (contents != null) {
            fromContents(contents);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (tiers > 0) {
            components.set(DrawerContent.COMPACTING_CONTENTS.get(), toContents());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Tiers");
        tag.remove("Amount");
    }
}
