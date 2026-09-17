package com.nightbeam.simpleterminals.registry;

import com.nightbeam.simpleterminals.menu.CraftingTerminalMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

public class ModMenus {
    private static Supplier<MenuType<CraftingTerminalMenu>> craftingTerminalMenu;

    public static void setCraftingTerminalMenu(Supplier<MenuType<CraftingTerminalMenu>> menuType) {
        craftingTerminalMenu = menuType;
    }

    public static MenuType<CraftingTerminalMenu> craftingTerminalMenu() {
        if (craftingTerminalMenu == null) {
            throw new IllegalStateException("Crafting terminal menu type has not been registered yet.");
        }
        return craftingTerminalMenu.get();
    }
}
