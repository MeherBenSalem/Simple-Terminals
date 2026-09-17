package com.nightbeam.simpleterminals;

import com.nightbeam.simpleterminals.menu.CraftingTerminalMenu;
import com.nightbeam.simpleterminals.network.CraftingTerminalScrollPacket;
import com.nightbeam.simpleterminals.network.CraftingTerminalSearchPacket;
import com.nightbeam.simpleterminals.registry.ModBlocks;
import com.nightbeam.simpleterminals.registry.ModItems;
import com.nightbeam.simpleterminals.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleContainer;

public class SimpleTerminals implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal"), ModBlocks.CRAFTING_TERMINAL);
        Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal"), ModItems.CRAFTING_TERMINAL);
        MenuType<CraftingTerminalMenu> menuType = Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal"),
                new ExtendedMenuType<>(
                        (syncId, inventory, slotCount) -> new CraftingTerminalMenu(syncId, inventory, new SimpleContainer(slotCount)),
                        ByteBufCodecs.VAR_INT
                )
        );
        ModMenus.setCraftingTerminalMenu(() -> menuType);

        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "main"),
                FabricCreativeModeTab.builder()
                        .title(Component.literal("Simple Terminals"))
                        .icon(() -> new ItemStack(ModItems.CRAFTING_TERMINAL))
                        .displayItems((parameters, entries) -> entries.accept(ModItems.CRAFTING_TERMINAL))
                        .build()
        );

        PayloadTypeRegistry.serverboundPlay().register(CraftingTerminalScrollPacket.TYPE, CraftingTerminalScrollPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CraftingTerminalSearchPacket.TYPE, CraftingTerminalSearchPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CraftingTerminalScrollPacket.TYPE, (payload, context) ->
                context.server().execute(() -> CraftingTerminalMenu.handleScroll(context.player(), payload.containerId(), payload.offsetRows())));
        ServerPlayNetworking.registerGlobalReceiver(CraftingTerminalSearchPacket.TYPE, (payload, context) ->
                context.server().execute(() -> CraftingTerminalMenu.handleSearch(context.player(), payload.containerId(), payload.query())));

        CommonClass.init();
    }
}
