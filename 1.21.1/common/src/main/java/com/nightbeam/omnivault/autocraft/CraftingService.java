package com.nightbeam.omnivault.autocraft;

import com.nightbeam.omnivault.autocraft.menu.CraftRequestMenu;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.CraftingHooks;
import com.nightbeam.omnivault.network.block.MenuDevice;
import com.nightbeam.omnivault.network.item.WirelessTerminalItem;
import com.nightbeam.omnivault.network.menu.TerminalHost;
import com.nightbeam.omnivault.network.menu.TerminalMenu;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Autocrafting's side of {@link CraftingHooks}: what terminals can craft, the request flow and job ticking. */
public final class CraftingService implements CraftingHooks.Provider {
    @Override
    public Set<ItemKey> craftables(StorageNetwork network) {
        return CraftingManager.of(network).craftables();
    }

    @Override
    public void requestCraft(ServerPlayer player, StorageNetwork network, ItemKey key, int containerId) {
        if (!(player.containerMenu instanceof TerminalMenu terminal) || terminal.containerId != containerId || terminal.host() == null) {
            return;
        }
        if (!CraftingManager.of(network).craftables().contains(key)) {
            return;
        }
        CraftRequestMenu.open(player, terminal.host(), key, reopener(player, terminal.host()));
    }

    @Override
    public void tick(StorageNetwork network, long gameTime) {
        CraftingManager.of(network).tick(gameTime);
    }

    /** How to get the player back to the terminal they came from, or null if it cannot be reopened. */
    @Nullable
    public static Runnable reopener(ServerPlayer player, TerminalHost host) {
        if (host instanceof MenuDevice device) {
            return () -> device.openMenu(player);
        }
        if (host instanceof WirelessTerminalItem.Host wireless) {
            return () -> WirelessTerminalItem.open(player, wireless.slot());
        }
        return null;
    }
}
