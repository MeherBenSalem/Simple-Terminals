package com.nightbeam.simpleterminals;

import com.nightbeam.simpleterminals.menu.CraftingTerminalMenu;
import com.nightbeam.simpleterminals.network.CraftingTerminalScrollPacket;
import com.nightbeam.simpleterminals.network.CraftingTerminalSearchPacket;
import com.nightbeam.simpleterminals.registry.ModBlocks;
import com.nightbeam.simpleterminals.registry.ModItems;
import com.nightbeam.simpleterminals.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleContainer;

public class SimpleTerminals implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal"), ModBlocks.CRAFTING_TERMINAL);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal"), ModItems.CRAFTING_TERMINAL);
        MenuType<CraftingTerminalMenu> menuType = Registry.register(
                BuiltInRegistries.MENU,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal"),
                new ExtendedScreenHandlerType<>(
                        (syncId, inventory, slotCount) -> new CraftingTerminalMenu(syncId, inventory, new SimpleContainer(slotCount)),
                        ByteBufCodecs.VAR_INT
                )
        );
        ModMenus.setCraftingTerminalMenu(() -> menuType);

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(entries -> entries.accept(ModItems.CRAFTING_TERMINAL));

        PayloadTypeRegistry.playC2S().register(CraftingTerminalScrollPacket.TYPE, CraftingTerminalScrollPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CraftingTerminalSearchPacket.TYPE, CraftingTerminalSearchPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CraftingTerminalScrollPacket.TYPE, (payload, context) ->
                context.server().execute(() -> CraftingTerminalMenu.handleScroll(context.player(), payload.containerId(), payload.offsetRows())));
        ServerPlayNetworking.registerGlobalReceiver(CraftingTerminalSearchPacket.TYPE, (payload, context) ->
                context.server().execute(() -> CraftingTerminalMenu.handleSearch(context.player(), payload.containerId(), payload.query())));

        CommonClass.init();
    }
}
