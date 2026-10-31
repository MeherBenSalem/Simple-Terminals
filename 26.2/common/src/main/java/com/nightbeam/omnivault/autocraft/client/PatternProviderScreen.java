package com.nightbeam.omnivault.autocraft.client;

import com.nightbeam.omnivault.autocraft.menu.PatternProviderMenu;
import com.nightbeam.omnivault.client.gui.GuiStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PatternProviderScreen extends AbstractContainerScreen<PatternProviderMenu> {
    public PatternProviderScreen(PatternProviderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, PatternProviderMenu.PLAYER_Y + 82);
        inventoryLabelY = PatternProviderMenu.PLAYER_Y - 11;
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
        int x = leftPos + PatternProviderMenu.SLOT_X;
        int y = topPos + PatternProviderMenu.SLOT_Y;
        GuiStyle.panel(graphics, x - 6, y - 6, 3 * 18 + 10, 3 * 18 + 10);
        graphics.fill(x - 5, y - 5, x + 3 * 18 + 3, y - 4, GuiStyle.VIOLET);
        GuiStyle.slots(graphics, x - 1, y - 1, 3, 3);
        // Output port on the right, like the block's working face.
        int px = x + 3 * 18 + 14;
        int py = y + 18;
        GuiStyle.well(graphics, px, py, 18, 18);
        graphics.fill(px + 4, py + 4, px + 14, py + 14, GuiStyle.ACCENT_DIM);
        graphics.fill(px + 6, py + 6, px + 12, py + 12, GuiStyle.ACCENT);
        GuiStyle.playerInventory(graphics, leftPos + 8, topPos + PatternProviderMenu.PLAYER_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GuiStyle.title(graphics, font, title, 8, 6);
        GuiStyle.labelDim(graphics, font, playerInventoryTitle, 8, inventoryLabelY);
        graphics.pose().pushMatrix();
        graphics.pose().scale(0.75f, 0.75f);
        graphics.textWithWordWrap(font, Component.translatable("gui.omnivault.provider.hint"), (int) (8 / 0.75f), (int) (22 / 0.75f),
                (int) (48 / 0.75f), GuiStyle.TEXT_DIM);
        graphics.pose().popMatrix();
    }
}
