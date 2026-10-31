package com.nightbeam.omnivault.network.client;

import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.client.gui.OVButton;
import com.nightbeam.omnivault.network.menu.CraftingTerminalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CraftingTerminalScreen extends TerminalScreen<CraftingTerminalMenu> {
    public CraftingTerminalScreen(CraftingTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new OVButton(leftPos + 8, topPos + menu.craftTop(), 12, 12, () -> Component.literal("×"),
                () -> Component.translatable("gui.omnivault.terminal.clear_grid"),
                () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CraftingTerminalMenu.BUTTON_CLEAR)));
    }

    @Override
    protected void renderExtraBackground(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = topPos + menu.craftTop();
        GuiStyle.panel(graphics, leftPos + 22, top - 4, 150, 3 * 18 + 8);
        GuiStyle.slots(graphics, leftPos + CraftingTerminalMenu.CRAFT_X - 1, top - 1, 3, 3);
        // Arrow towards the result.
        int ax = leftPos + 88;
        int ay = top + 22;
        graphics.fill(ax, ay + 3, ax + 18, ay + 7, GuiStyle.ACCENT_DIM);
        for (int i = 0; i < 6; i++) {
            graphics.fill(ax + 18 + i, ay - 1 + i, ax + 19 + i, ay + 11 - i, GuiStyle.ACCENT);
        }
        GuiStyle.bigSlot(graphics, leftPos + CraftingTerminalMenu.RESULT_X - 5, top + 13, 26, GuiStyle.ACCENT);
    }
}
