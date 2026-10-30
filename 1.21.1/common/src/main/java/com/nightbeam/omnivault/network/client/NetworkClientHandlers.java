package com.nightbeam.omnivault.network.client;

import com.nightbeam.omnivault.network.NetworkPackets;
import com.nightbeam.omnivault.network.menu.CoreMenu;
import com.nightbeam.omnivault.network.menu.TerminalMenu;
import net.minecraft.client.Minecraft;

/** Client-side payload handlers. Only ever loaded on the client. */
public final class NetworkClientHandlers {
    private NetworkClientHandlers() {
    }

    public static void gridContents(NetworkPackets.GridContents payload) {
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.containerMenu instanceof TerminalMenu menu
                && menu.containerId == payload.containerId()) {
            menu.applyContents(payload);
        }
    }

    public static void coreStats(NetworkPackets.CoreStats payload) {
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.containerMenu instanceof CoreMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setStats(payload);
        }
    }
}
