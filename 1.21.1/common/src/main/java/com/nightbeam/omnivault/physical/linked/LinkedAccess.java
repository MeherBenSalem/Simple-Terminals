package com.nightbeam.omnivault.physical.linked;

import com.nightbeam.omnivault.physical.PhysicalContent;
import com.nightbeam.omnivault.physical.StorageLayout;
import com.nightbeam.omnivault.physical.StorageMenu;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Opens the shared inventory of a frequency, from a vault or from a pouch. */
public final class LinkedAccess {
    public static final StorageLayout LAYOUT = new StorageLayout(9, 3);

    private LinkedAccess() {
    }

    public static void open(ServerPlayer player, LinkedFrequency frequency, Component title, StorageMenu.Source source) {
        LinkedVaultData.Inventory inventory = LinkedVaultData.get(player.server).inventory(frequency);
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider(
                        (containerId, playerInventory, p) -> new StorageMenu(PhysicalContent.LINKED_MENU.get(), containerId, playerInventory,
                                inventory, LAYOUT, source, null, frequency), title),
                buf -> LinkedFrequency.STREAM_CODEC.encode(buf, frequency));
    }
}
