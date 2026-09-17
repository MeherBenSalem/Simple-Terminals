package com.nightbeam.simpleterminals.client;

import com.nightbeam.simpleterminals.Constants;
import com.nightbeam.simpleterminals.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class SimpleTerminalsClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.craftingTerminalMenu(), CraftingTerminalScreen::new);
    }
}
