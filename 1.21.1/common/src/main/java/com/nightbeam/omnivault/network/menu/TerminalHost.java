package com.nightbeam.omnivault.network.menu;

import com.nightbeam.omnivault.network.StorageNetwork;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** What a terminal menu talks to: a terminal block or a wireless terminal in a player's hand. */
public interface TerminalHost {
    @Nullable
    StorageNetwork network();

    boolean stillValid(Player player);

    /** The 3×3 crafting grid that persists with the host, or null if items go back on close. */
    @Nullable
    default NonNullList<ItemStack> persistentCraftingGrid() {
        return null;
    }

    default void setChanged() {
    }
}
