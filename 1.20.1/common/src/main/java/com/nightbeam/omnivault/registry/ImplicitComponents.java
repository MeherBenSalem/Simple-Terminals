package com.nightbeam.omnivault.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A block entity whose data travels with its item, as block entities of later Minecraft versions do
 * with implicit data components: written to the dropped item by the {@code omnivault:copy_components}
 * loot function (see {@link CopyComponentsFunction}) and read back when the item is placed.
 */
public interface ImplicitComponents {
    /** Reads this block entity's data from a placed item. */
    void applyImplicitComponents(ItemStack stack);

    /** Writes this block entity's data to an item. */
    void collectImplicitComponents(ItemStack stack);

    /** Blocks call this from {@code setPlacedBy}, on both sides. */
    static void applyOnPlace(Level level, BlockPos pos, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof ImplicitComponents holder) {
            holder.applyImplicitComponents(stack);
        }
    }
}
