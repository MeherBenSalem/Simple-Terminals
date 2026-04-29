package com.nightbeam.simpleterminals.client;

import com.nightbeam.simpleterminals.menu.CraftingTerminalMenu;
import com.nightbeam.simpleterminals.platform.Services;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CraftingTerminalScreen extends AbstractContainerScreen<CraftingTerminalMenu> {
    private static final int SCROLLBAR_X = 174;
    private static final int SCROLLBAR_Y = 34;
    private static final int SCROLLBAR_HEIGHT = 108;
    private boolean scrolling;
    private EditBox searchBox;

    public CraftingTerminalScreen(CraftingTerminalMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 304;
        imageHeight = 240;
        inventoryLabelY = 147;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, leftPos + 8, topPos + 17, 162, 14, Component.literal("Search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(50);
        searchBox.setResponder(query -> {
            menu.setSearchQuery(query);
            Services.PLATFORM.sendCraftingTerminalSearch(menu.containerId, query);
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF1F2328);
        graphics.fill(leftPos + 4, topPos + 14, leftPos + 186, topPos + 146, 0xFF2D333B);
        graphics.fill(leftPos + 194, topPos + 34, leftPos + 294, topPos + 130, 0xFF2D333B);
        graphics.fill(leftPos + 4, topPos + 154, leftPos + 170, topPos + 236, 0xFF2D333B);
        graphics.fill(leftPos + 7, topPos + 16, leftPos + 171, topPos + 32, 0xFF111318);

        for (int row = 0; row < CraftingTerminalMenu.VISIBLE_ROWS; row++) {
            for (int column = 0; column < CraftingTerminalMenu.COLUMNS; column++) {
                int x = leftPos + 7 + column * 18;
                int y = topPos + 33 + row * 18;
                graphics.fill(x, y, x + 18, y + 18, 0xFF111318);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF252A31);
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                drawSlotBackground(graphics, leftPos + 201 + column * 18, topPos + 51 + row * 18);
            }
        }
        drawSlotBackground(graphics, leftPos + 273, topPos + 69);
        graphics.fill(leftPos + 258, topPos + 77, leftPos + 267, topPos + 79, 0xFFB9C7D5);
        graphics.fill(leftPos + 265, topPos + 74, leftPos + 268, topPos + 82, 0xFFB9C7D5);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlotBackground(graphics, leftPos + 7 + column * 18, topPos + 157 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlotBackground(graphics, leftPos + 7 + column * 18, topPos + 215);
        }

        drawScrollbar(graphics);
    }

    private static void drawSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF111318);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF252A31);
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int x = leftPos + SCROLLBAR_X;
        int y = topPos + SCROLLBAR_Y;
        graphics.fill(x, y, x + 8, y + SCROLLBAR_HEIGHT, 0xFF111318);
        int thumbHeight = menu.getMaxOffsetRows() == 0 ? SCROLLBAR_HEIGHT : Math.max(16, SCROLLBAR_HEIGHT * CraftingTerminalMenu.VISIBLE_ROWS / Math.max(CraftingTerminalMenu.VISIBLE_ROWS, (menu.getTargetSlotCount() + 8) / 9));
        int travel = SCROLLBAR_HEIGHT - thumbHeight;
        int thumbY = y + (menu.getMaxOffsetRows() == 0 ? 0 : travel * menu.getOffsetRows() / menu.getMaxOffsetRows());
        graphics.fill(x + 1, thumbY, x + 7, thumbY + thumbHeight, 0xFFB9C7D5);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE6EDF3, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xE6EDF3, false);
        graphics.drawString(font, Component.literal("Crafting"), 202, 40, 0xE6EDF3, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            graphics.drawString(font, "Search", leftPos + 9, topPos + 20, 0xFF6E7681, false);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (menu.getMaxOffsetRows() > 0) {
            sendScroll(menu.getOffsetRows() - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInScrollbar(mouseX, mouseY)) {
            scrolling = true;
            scrollTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrolling) {
            scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isInScrollbar(double mouseX, double mouseY) {
        return mouseX >= leftPos + SCROLLBAR_X && mouseX < leftPos + SCROLLBAR_X + 8
                && mouseY >= topPos + SCROLLBAR_Y && mouseY < topPos + SCROLLBAR_Y + SCROLLBAR_HEIGHT;
    }

    private void scrollTo(double mouseY) {
        int max = menu.getMaxOffsetRows();
        if (max <= 0) {
            return;
        }
        double relative = (mouseY - (topPos + SCROLLBAR_Y)) / SCROLLBAR_HEIGHT;
        sendScroll((int) Math.round(relative * max));
    }

    private void sendScroll(int offsetRows) {
        int clamped = Math.max(0, Math.min(offsetRows, menu.getMaxOffsetRows()));
        menu.setScrollOffsetRows(clamped);
        Services.PLATFORM.sendCraftingTerminalScroll(menu.containerId, clamped);
    }
}
