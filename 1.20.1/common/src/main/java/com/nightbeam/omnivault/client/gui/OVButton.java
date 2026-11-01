package com.nightbeam.omnivault.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** A compact GuiStyle button showing a short glyph or label, with a live tooltip. */
public class OVButton extends AbstractButton {
    private final Supplier<Component> label;
    private final Supplier<Component> tooltip;
    private final Runnable action;
    private Component lastTooltip;

    public OVButton(int x, int y, int width, int height, Supplier<Component> label, Supplier<Component> tooltip, Runnable action) {
        super(x, y, width, height, label.get());
        this.label = label;
        this.tooltip = tooltip;
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiStyle.button(graphics, getX(), getY(), width, height, isHoveredOrFocused(), false);
        Component text = label.get();
        var font = Minecraft.getInstance().font;
        int color = active ? GuiStyle.TEXT : GuiStyle.TEXT_DIM;
        graphics.drawString(font, text, getX() + (width - font.width(text)) / 2 + 1, getY() + (height - 8) / 2 + 1, color, false);
        Component tip = tooltip.get();
        if (tip != null && !tip.equals(lastTooltip)) {
            lastTooltip = tip;
            setTooltip(Tooltip.create(tip));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
