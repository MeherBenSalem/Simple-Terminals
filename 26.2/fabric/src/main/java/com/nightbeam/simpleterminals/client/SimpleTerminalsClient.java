package com.nightbeam.simpleterminals.client;

import com.nightbeam.simpleterminals.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class SimpleTerminalsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.craftingTerminalMenu(), CraftingTerminalScreen::new);
    }
}
