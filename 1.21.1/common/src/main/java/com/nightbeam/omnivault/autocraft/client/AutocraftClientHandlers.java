package com.nightbeam.omnivault.autocraft.client;

import com.nightbeam.omnivault.autocraft.AutocraftPackets;
import com.nightbeam.omnivault.autocraft.menu.CraftRequestMenu;
import com.nightbeam.omnivault.autocraft.menu.CraftingStatusMenu;
import net.minecraft.client.Minecraft;

/** Client-side payload handlers. Only ever loaded on the client. */
public final class AutocraftClientHandlers {
    private AutocraftClientHandlers() {
    }

    public static void plan(AutocraftPackets.PlanResult payload) {
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.containerMenu instanceof CraftRequestMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setPlan(payload.plan());
        }
    }

    public static void jobs(AutocraftPackets.JobList payload) {
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.containerMenu instanceof CraftingStatusMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setJobs(payload);
        }
    }
}
