package com.nightbeam.omnivault.network.client;

import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.client.gui.OVButton;
import com.nightbeam.omnivault.network.NetworkPackets;
import com.nightbeam.omnivault.network.menu.SignalEmitterMenu;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public class SignalEmitterScreen extends AbstractContainerScreen<SignalEmitterMenu> {
    private EditBox amount;

    public SignalEmitterScreen(SignalEmitterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = SignalEmitterMenu.PLAYER_Y + 82;
        inventoryLabelY = SignalEmitterMenu.PLAYER_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        amount = new EditBox(font, leftPos + 56, topPos + 40, 80, 10, Component.translatable("gui.omnivault.emitter.threshold"));
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (amount.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            amount.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiStyle.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        GuiStyle.panel(graphics, leftPos + 20, topPos + 29, 30, 30);
        GuiStyle.slot(graphics, leftPos + 25, topPos + 34);
        GuiStyle.well(graphics, leftPos + 53, topPos + 37, 84, 14);
        GuiStyle.playerInventory(graphics, leftPos + 8, topPos + SignalEmitterMenu.PLAYER_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiStyle.title(graphics, font, title, 8, 6);
        GuiStyle.labelDim(graphics, font, playerInventoryTitle, 8, inventoryLabelY);
        GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.emitter.threshold"), 56, 26);
        GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.emitter.hint"), 8, 62);
    }
}
