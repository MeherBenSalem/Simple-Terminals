package com.nightbeam.simpleterminals;

import com.nightbeam.simpleterminals.menu.CraftingTerminalMenu;
import com.nightbeam.simpleterminals.registry.ModBlocks;
import com.nightbeam.simpleterminals.registry.ModItems;
import com.nightbeam.simpleterminals.registry.ModMenus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Constants.MOD_ID)
public class SimpleTerminals {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, Constants.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, Constants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, Constants.MOD_ID);

    public static final DeferredHolder<Block, Block> CRAFTING_TERMINAL_BLOCK =
            BLOCKS.register("crafting_terminal", () -> ModBlocks.CRAFTING_TERMINAL);
    public static final DeferredHolder<Item, Item> CRAFTING_TERMINAL_ITEM =
            ITEMS.register("crafting_terminal", () -> ModItems.CRAFTING_TERMINAL);
    public static final DeferredHolder<MenuType<?>, MenuType<CraftingTerminalMenu>> CRAFTING_TERMINAL_MENU =
            MENUS.register("crafting_terminal", () -> IMenuTypeExtension.create(CraftingTerminalMenu::new));

    public SimpleTerminals(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        ModMenus.setCraftingTerminalMenu(CRAFTING_TERMINAL_MENU);
        modEventBus.addListener(NeoForgeNetworking::register);
        modEventBus.addListener(this::addCreative);
        CommonClass.init();
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.CRAFTING_TERMINAL);
        }
    }
}
