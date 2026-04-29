package com.nightbeam.simpleterminals;

import com.nightbeam.simpleterminals.menu.CraftingTerminalMenu;
import com.nightbeam.simpleterminals.registry.ModBlocks;
import com.nightbeam.simpleterminals.registry.ModItems;
import com.nightbeam.simpleterminals.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.extensions.IForgeMenuType;

@Mod(Constants.MOD_ID)
public class SimpleTerminals {
    private static final String NETWORK_VERSION = "1";
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Constants.MOD_ID, "main"),
            () -> NETWORK_VERSION,
            NETWORK_VERSION::equals,
            NETWORK_VERSION::equals
    );

    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Constants.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.item.Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Constants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Constants.MOD_ID);

    public static final RegistryObject<net.minecraft.world.level.block.Block> CRAFTING_TERMINAL_BLOCK =
            BLOCKS.register("crafting_terminal", () -> ModBlocks.CRAFTING_TERMINAL);
    public static final RegistryObject<net.minecraft.world.item.Item> CRAFTING_TERMINAL_ITEM =
            ITEMS.register("crafting_terminal", () -> ModItems.CRAFTING_TERMINAL);
    public static final RegistryObject<MenuType<CraftingTerminalMenu>> CRAFTING_TERMINAL_MENU =
            MENUS.register("crafting_terminal", () -> IForgeMenuType.create(CraftingTerminalMenu::new));

    public SimpleTerminals() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        ModMenus.setCraftingTerminalMenu(CRAFTING_TERMINAL_MENU);
        ForgeNetworking.register();
        modBus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(this);
        CommonClass.init();
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.CRAFTING_TERMINAL);
        }
    }
}
