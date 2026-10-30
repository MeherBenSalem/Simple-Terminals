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
import java.util.Optional;

/** A 1×1, 1×2 or 2×2 drawer: each compartment holds one item type. */
public class DrawerBlockEntity extends AbstractDrawerBlockEntity {
    private final ItemKey[] keys;
    private final long[] counts;

    public DrawerBlockEntity(BlockPos pos, BlockState state) {
        super(DrawerContent.DRAWER_BE.get(), pos, state);
        int size = layoutOf(state).compartments();
        keys = new ItemKey[size];
        counts = new long[size];
    }

    @Override
    public int compartments() {
        return keys.length;
    }

    @Override
    @Nullable
    public ItemKey keyAt(int slot) {
        return keys[slot];
    }

    @Override
    public long countAt(int slot) {
        return counts[slot];
    }

    @Override
    public long capacityAt(int slot, int multiplier) {
        return capacityFor(keys[slot], multiplier);
    }

    private long capacityFor(@Nullable ItemKey key, int multiplier) {
        int perStack = key == null ? 64 : key.maxStackSize();
        return (long) layout().baseStacks() * multiplier * perStack;
    }

    @Override
    public boolean accepts(int slot, ItemKey key) {
        return keys[slot] == null || keys[slot].equals(key);
    }

    @Override
    public long insert(int slot, ItemKey key, long amount, boolean simulate) {
        if (amount <= 0 || !accepts(slot, key)) {
            return 0;
        }
        long room = Math.max(0, capacityFor(key, capacityMultiplier()) - counts[slot]);
        long accepted = Math.min(amount, room);
        if (!simulate && accepted > 0) {
            keys[slot] = key;
            counts[slot] += accepted;
            contentsChanged();
        }
        return isVoiding() ? amount : accepted;
    }

    @Override
    public long extract(int slot, long amount, boolean simulate) {
        if (amount <= 0 || keys[slot] == null || counts[slot] <= 0) {
            return 0;
        }
        long taken = Math.min(amount, counts[slot]);
        if (!simulate) {
            counts[slot] -= taken;
            if (counts[slot] <= 0 && !isLocked()) {
                keys[slot] = null;
            }
            contentsChanged();
        }
        return taken;
    }

    @Override
    protected void forgetEmptyTypes() {
        for (int slot = 0; slot < keys.length; slot++) {
            if (counts[slot] <= 0) {
                keys[slot] = null;
            }
        }
    }

    @Override
    public double fillFraction() {
        double total = 0;
        int used = 0;
        for (int slot = 0; slot < keys.length; slot++) {
            if (keys[slot] != null) {
                total += (double) counts[slot] / Math.max(1, capacityAt(slot));
                used++;
            }
        }
        return used == 0 ? 0 : total / keys.length;
    }

    // ---------------------------------------------------------------------------------------------

    public DrawerContents toContents() {
        List<DrawerContents.Entry> entries = new ArrayList<>(keys.length);
        for (int slot = 0; slot < keys.length; slot++) {
            entries.add(new DrawerContents.Entry(Optional.ofNullable(keys[slot]), counts[slot]));
        }
        return new DrawerContents(entries);
    }

    private void fromContents(DrawerContents contents) {
        for (int slot = 0; slot < keys.length; slot++) {
            DrawerContents.Entry entry = slot < contents.entries().size() ? contents.entries().get(slot) : null;
            keys[slot] = entry == null ? null : entry.key().orElse(null);
            counts[slot] = entry == null || keys[slot] == null ? 0 : Math.max(0, entry.count());
        }
    }

    @Override
    protected void saveContents(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < keys.length; slot++) {
            CompoundTag entry = new CompoundTag();
            if (keys[slot] != null) {
                entry.put("Item", keys[slot].save(registries));
                entry.putLong("Count", counts[slot]);
            }
            list.add(entry);
        }
        tag.put("Compartments", list);
    }

    @Override
    protected void loadContents(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = tag.getList("Compartments", Tag.TAG_COMPOUND);
        for (int slot = 0; slot < keys.length; slot++) {
            CompoundTag entry = slot < list.size() ? list.getCompound(slot) : new CompoundTag();
            keys[slot] = entry.contains("Item") ? ItemKey.load(registries, entry.get("Item")) : null;
            counts[slot] = keys[slot] == null ? 0 : Math.max(0, entry.getLong("Count"));
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        DrawerContents contents = input.get(DrawerContent.DRAWER_CONTENTS.get());
        if (contents != null) {
            fromContents(contents);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        DrawerContents contents = toContents();
        if (!contents.entries().stream().allMatch(e -> e.key().isEmpty())) {
            components.set(DrawerContent.DRAWER_CONTENTS.get(), contents);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Compartments");
    }
}
