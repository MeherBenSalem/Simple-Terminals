package com.nightbeam.omnivault.network.client;

import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.client.gui.OVButton;
import com.nightbeam.omnivault.network.menu.BusMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public class BusScreen extends AbstractContainerScreen<BusMenu> {
    public BusScreen(BusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = BusMenu.PLAYER_Y + 82;
        inventoryLabelY = BusMenu.PLAYER_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.kind() != BusMenu.Kind.EXPORT) {
            addRenderableWidget(new OVButton(leftPos + 8, topPos + 22, 18, 18,
                    () -> Component.literal(menu.denyList() ? "✖" : "✔"),
                    () -> Component.translatable(menu.denyList() ? "gui.omnivault.filter.deny" : "gui.omnivault.filter.allow"),
                    () -> click(BusMenu.BUTTON_MODE)));
        }
        if (menu.kind() == BusMenu.Kind.STORAGE) {
            addRenderableWidget(new OVButton(leftPos + 8, topPos + 42, 18, 18,
                    () -> Component.literal(switch (menu.access()) {
                        case BOTH -> "⇅";
                        case INSERT_ONLY -> "↓";
                        case EXTRACT_ONLY -> "↑";
                    }),
                    () -> Component.translatable("gui.omnivault.access." + menu.access().name().toLowerCase(Locale.ROOT)),
                    () -> click(BusMenu.BUTTON_ACCESS)));
            addRenderableWidget(new OVButton(leftPos + 8, topPos + 74, 12, 12, () -> Component.literal("-"),
                    () -> Component.translatable("gui.omnivault.priority.tooltip"),
                    () -> click(BusMenu.BUTTON_PRIORITY_BASE + (hasShiftDown() ? 0 : 1))));
            addRenderableWidget(new OVButton(leftPos + 40, topPos + 74, 12, 12, () -> Component.literal("+"),
                    () -> Component.translatable("gui.omnivault.priority.tooltip"),
                    () -> click(BusMenu.BUTTON_PRIORITY_BASE + (hasShiftDown() ? 3 : 2))));
        }
    }

    private void click(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiStyle.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        GuiStyle.panel(graphics, leftPos + BusMenu.FILTER_X - 5, topPos + BusMenu.FILTER_Y - 5, 62, 62);
        GuiStyle.slots(graphics, leftPos + BusMenu.FILTER_X - 1, topPos + BusMenu.FILTER_Y - 1, 3, 3);
        GuiStyle.panel(graphics, leftPos + BusMenu.UPGRADE_X - 5, topPos + 4, 26, 80);
        GuiStyle.slots(graphics, leftPos + BusMenu.UPGRADE_X - 1, topPos + 7, 1, 4);
        GuiStyle.playerInventory(graphics, leftPos + 8, topPos + BusMenu.PLAYER_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiStyle.title(graphics, font, title, 8, 6);
        GuiStyle.labelDim(graphics, font, playerInventoryTitle, 8, inventoryLabelY);
        if (menu.kind() == BusMenu.Kind.STORAGE) {
            GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.priority"), 8, 64);
            GuiStyle.centered(graphics, font, Component.literal(String.valueOf(menu.priority())), 30, 76, GuiStyle.TEXT);
        }
    }
}
