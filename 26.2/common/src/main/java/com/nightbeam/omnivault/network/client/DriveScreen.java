package com.nightbeam.omnivault.network.client;

import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.client.gui.OVButton;
import com.nightbeam.omnivault.network.menu.DriveMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class DriveScreen extends AbstractContainerScreen<DriveMenu> {
    public DriveScreen(DriveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, DriveMenu.PLAYER_Y + 82);
        inventoryLabelY = DriveMenu.PLAYER_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new OVButton(leftPos + 116, topPos + 64, 12, 12, () -> Component.literal("-"),
                () -> Component.translatable("gui.omnivault.priority.tooltip"),
                () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, minecraft.hasShiftDown() ? 0 : 1)));
        addRenderableWidget(new OVButton(leftPos + 154, topPos + 64, 12, 12, () -> Component.literal("+"),
                () -> Component.translatable("gui.omnivault.priority.tooltip"),
                () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, minecraft.hasShiftDown() ? 3 : 2)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiStyle.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        GuiStyle.panel(graphics, leftPos + DriveMenu.CELL_X - 5, topPos + DriveMenu.CELL_Y - 5, 46, 100);
        GuiStyle.slots(graphics, leftPos + DriveMenu.CELL_X - 1, topPos + DriveMenu.CELL_Y - 1, 2, 5);
        GuiStyle.playerInventory(graphics, leftPos + 8, topPos + DriveMenu.PLAYER_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GuiStyle.title(graphics, font, title, 8, 6);
        GuiStyle.labelDim(graphics, font, playerInventoryTitle, 8, inventoryLabelY);
        GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.priority"), 116, 50);
        GuiStyle.centered(graphics, font, Component.literal(String.valueOf(menu.priority())), 141, 66, GuiStyle.TEXT);
    }
}
