package com.nightbeam.simpleterminals;

import com.nightbeam.simpleterminals.registry.ModBlocks;
import com.nightbeam.simpleterminals.registry.ModItems;
import com.nightbeam.simpleterminals.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.inventory.MenuType;

public class SimpleTerminals implements ModInitializer {
    public static final ResourceLocation CRAFTING_TERMINAL_SCROLL = new ResourceLocation(Constants.MOD_ID, "crafting_terminal_scroll");
    public static final ResourceLocation CRAFTING_TERMINAL_SEARCH = new ResourceLocation(Constants.MOD_ID, "crafting_terminal_search");

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(Constants.MOD_ID, "crafting_terminal"), ModBlocks.CRAFTING_TERMINAL);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(Constants.MOD_ID, "crafting_terminal"), ModItems.CRAFTING_TERMINAL);
        MenuType<com.nightbeam.simpleterminals.menu.CraftingTerminalMenu> menuType = ScreenHandlerRegistry.registerExtended(
                new ResourceLocation(Constants.MOD_ID, "crafting_terminal"),
                com.nightbeam.simpleterminals.menu.CraftingTerminalMenu::new
        );
        ModMenus.setCraftingTerminalMenu(() -> menuType);

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(entries -> entries.accept(ModItems.CRAFTING_TERMINAL));

        ServerPlayNetworking.registerGlobalReceiver(CRAFTING_TERMINAL_SCROLL, (server, player, handler, buffer, responseSender) -> {
            int containerId = buffer.readVarInt();
            int offsetRows = buffer.readVarInt();
            server.execute(() -> com.nightbeam.simpleterminals.menu.CraftingTerminalMenu.handleScroll(player, containerId, offsetRows));
        });
        ServerPlayNetworking.registerGlobalReceiver(CRAFTING_TERMINAL_SEARCH, (server, player, handler, buffer, responseSender) -> {
            int containerId = buffer.readVarInt();
            String query = buffer.readUtf(50);
            server.execute(() -> com.nightbeam.simpleterminals.menu.CraftingTerminalMenu.handleSearch(player, containerId, query));
        });

        CommonClass.init();
    }
}
