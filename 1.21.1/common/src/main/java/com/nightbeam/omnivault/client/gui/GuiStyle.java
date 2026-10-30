package com.nightbeam.omnivault.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The OmniVault screen look: gunmetal panels, recessed slot wells and a cyan accent. Every screen
 * draws through these helpers so the whole mod stays consistent.
 */
public final class GuiStyle {
    public static final int OUTLINE = 0xFF07090D;
    public static final int PANEL = 0xFF1B2029;
    public static final int PANEL_LIGHT = 0xFF2A313D;
    public static final int PANEL_HIGHLIGHT = 0xFF3E4859;
    public static final int PANEL_SHADOW = 0xFF0F1218;
    public static final int WELL = 0xFF11151C;
    public static final int SLOT = 0xFF262D38;
    public static final int SLOT_HOVER = 0x6038E1FF;
    public static final int ACCENT = 0xFF38E1FF;
    public static final int ACCENT_DIM = 0xFF1A7F99;
    public static final int VIOLET = 0xFFA46BFF;
    public static final int TEXT = 0xFFE6EDF3;
    public static final int TEXT_DIM = 0xFF8B949E;
    public static final int GOOD = 0xFF5BE08A;
    public static final int WARN = 0xFFF2C14E;
    public static final int BAD = 0xFFFF6B6B;

    private GuiStyle() {
    }

    /** The main window: dark outline, bevelled gunmetal and an accent strip along the top. */
    public static void window(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, PANEL_HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, PANEL_HIGHLIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, PANEL_SHADOW);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, PANEL_SHADOW);
        g.fillGradient(x + 3, y + 2, x + w - 3, y + 3, ACCENT, ACCENT_DIM);
        // Corner rivets.
        rivet(g, x + 4, y + 5);
        rivet(g, x + w - 6, y + 5);
        rivet(g, x + 4, y + h - 6);
        rivet(g, x + w - 6, y + h - 6);
    }

    private static void rivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, PANEL_SHADOW);
        g.fill(x, y, x + 1, y + 1, PANEL_HIGHLIGHT);
    }

    /** A raised sub-panel inside a window. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_SHADOW);
        g.fill(x + 1, y + 1, x + w, y + h, PANEL_HIGHLIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_LIGHT);
    }

    /** A recessed area, e.g. behind a slot grid or a text field. */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_HIGHLIGHT);
        g.fill(x, y, x + w - 1, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, WELL);
    }

    /** One 18×18 slot whose item renders at (x + 1, y + 1). */
    public static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, OUTLINE);
        g.fill(x + 1, y + 1, x + 18, y + 18, PANEL_HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
        g.fill(x + 1, y + 1, x + 17, y + 2, PANEL_SHADOW);
        g.fill(x + 1, y + 1, x + 2, y + 17, PANEL_SHADOW);
    }

    /** A slot with a glowing frame, for outputs and special inputs. */
    public static void bigSlot(GuiGraphics g, int x, int y, int size, int glow) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, glow & 0x80FFFFFF);
        g.fill(x, y, x + size, y + size, OUTLINE);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, SLOT);
    }

    public static void slots(GuiGraphics g, int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                slot(g, x + column * 18, y + row * 18);
            }
        }
    }

    /** The standard 9×3 + hotbar player inventory, slot origin (x, y) as in the menu. */
    public static void playerInventory(GuiGraphics g, int x, int y) {
        slots(g, x - 1, y - 1, 9, 3);
        slots(g, x - 1, y + 57, 9, 1);
    }

    public static void scrollbar(GuiGraphics g, int x, int y, int h, float progress, boolean active) {
        well(g, x, y, 8, h);
        int thumb = 15;
        int ty = y + 1 + Math.round((h - 2 - thumb) * Math.max(0, Math.min(1, progress)));
        int color = active ? ACCENT : PANEL_HIGHLIGHT;
        g.fill(x + 1, ty, x + 7, ty + thumb, color);
        g.fill(x + 2, ty + 1, x + 6, ty + thumb - 1, active ? 0xFF8DF0FF : PANEL_LIGHT);
        g.fill(x + 2, ty + 6, x + 6, ty + 7, OUTLINE);
        g.fill(x + 2, ty + 8, x + 6, ty + 9, OUTLINE);
    }

    /** A progress or fill bar. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fill, int color) {
        well(g, x, y, w, h);
        int filled = Math.round((w - 2) * Math.max(0, Math.min(1, fill)));
        if (filled > 0) {
            g.fillGradient(x + 1, y + 1, x + 1 + filled, y + h - 1, color, darken(color));
        }
    }

    /** A small square icon button; returns nothing, hit-testing is up to the caller. */
    public static void button(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean pressed) {
        g.fill(x, y, x + w, y + h, OUTLINE);
        int top = pressed ? PANEL_SHADOW : PANEL_HIGHLIGHT;
        int bottom = pressed ? PANEL_HIGHLIGHT : PANEL_SHADOW;
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bottom);
        g.fill(x + 1, y + 1, x + w - 2, y + h - 2, top);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, hovered ? 0xFF35404F : PANEL_LIGHT);
        if (hovered) {
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, ACCENT);
        }
    }

    public static void label(GuiGraphics g, Font font, Component text, int x, int y) {
        g.drawString(font, text, x, y, TEXT, false);
    }

    public static void labelDim(GuiGraphics g, Font font, Component text, int x, int y) {
        g.drawString(font, text, x, y, TEXT_DIM, false);
    }

    public static void title(GuiGraphics g, Font font, Component text, int x, int y) {
        g.drawString(font, text, x, y, TEXT, true);
    }

    /** The text as is if it fits in {@code maxWidth} pixels, otherwise cut short with an ellipsis. */
    public static Component fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(0, maxWidth - font.width("…"))) + "…")
                .withStyle(text.getStyle());
    }

    public static void centered(GuiGraphics g, Font font, Component text, int cx, int y, int color) {
        FormattedCharSequence seq = text.getVisualOrderText();
        g.drawString(font, seq, cx - font.width(seq) / 2, y, color, false);
    }

    /** Draws an item count right-aligned in a slot at reduced scale, like large-storage UIs do. */
    public static void smallCount(GuiGraphics g, Font font, String text, int slotX, int slotY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        float scale = 0.5f;
        g.pose().scale(scale, scale, 1);
        int width = font.width(text);
        int tx = (int) ((slotX + 17) / scale) - width;
        int ty = (int) ((slotY + 17) / scale) - 8;
        g.drawString(font, text, tx, ty, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    public static int darken(int argb) {
        int a = argb >>> 24;
        int r = (argb >> 16 & 0xFF) * 3 / 5;
        int gr = (argb >> 8 & 0xFF) * 3 / 5;
        int b = (argb & 0xFF) * 3 / 5;
        return a << 24 | r << 16 | gr << 8 | b;
    }
}
