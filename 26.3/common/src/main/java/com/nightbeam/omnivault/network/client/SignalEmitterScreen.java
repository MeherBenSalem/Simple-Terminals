package com.nightbeam.omnivault.network.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nightbeam.omnivault.client.gui.FilteredEditBox;
import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.client.gui.OVButton;
import com.nightbeam.omnivault.network.NetworkPackets;
import com.nightbeam.omnivault.network.menu.SignalEmitterMenu;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SignalEmitterScreen extends AbstractContainerScreen<SignalEmitterMenu> {
    private FilteredEditBox amount;

    public SignalEmitterScreen(SignalEmitterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, SignalEmitterMenu.PLAYER_Y + 82);
        inventoryLabelY = SignalEmitterMenu.PLAYER_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        amount = new FilteredEditBox(font, leftPos + 56, topPos + 40, 80, 10, Component.translatable("gui.omnivault.emitter.threshold"));
        amount.setBordered(false);
        amount.setTextColor(GuiStyle.TEXT);
        amount.setFilter(text -> text.isEmpty() || text.matches("\\d{0,18}"));
        amount.setValue(String.valueOf(menu.threshold()));
        amount.setResponder(text -> {
            long value = text.isEmpty() ? 0 : Long.parseLong(text);
            menu.setThresholdClient(value);
            Services.PLATFORM.sendToServer(new NetworkPackets.MenuValue(menu.containerId, SignalEmitterMenu.VALUE_THRESHOLD, value));
        });
        addRenderableWidget(amount);
        addRenderableWidget(new OVButton(leftPos + 140, topPos + 36, 28, 16,
                () -> Component.literal(menu.below() ? "<" : "≥"),
                () -> Component.translatable(menu.below() ? "gui.omnivault.emitter.below" : "gui.omnivault.emitter.at_least"),
                () -> {
                    menu.setBelowClient(!menu.below());
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SignalEmitterMenu.BUTTON_MODE);
                }));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (amount.isFocused() && keyCode != InputConstants.KEY_ESCAPE) {
            amount.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
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
        GuiStyle.panel(graphics, leftPos + 20, topPos + 29, 30, 30);
        GuiStyle.slot(graphics, leftPos + 25, topPos + 34);
        GuiStyle.well(graphics, leftPos + 53, topPos + 37, 84, 14);
        GuiStyle.playerInventory(graphics, leftPos + 8, topPos + SignalEmitterMenu.PLAYER_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GuiStyle.title(graphics, font, title, 8, 6);
        GuiStyle.labelDim(graphics, font, playerInventoryTitle, 8, inventoryLabelY);
        GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.emitter.threshold"), 56, 26);
        GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.emitter.hint"), 8, 62);
    }
}
