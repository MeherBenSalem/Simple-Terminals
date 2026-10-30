package com.nightbeam.omnivault.physical.client;

import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.physical.trash.TrashInventory;
import com.nightbeam.omnivault.physical.trash.TrashMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Trash can screen: void slot with an animated vortex, recoverable last item, automation filter. */
public class TrashScreen extends AbstractContainerScreen<TrashMenu> {
    private static final int MODE_W = 58;
    private static final int BUTTON_H = 12;
    private static final int VORTEX_X = 74;
    private static final int VORTEX_Y = TrashMenu.TOP_ROW_Y - 6;
    private static final int VORTEX_SIZE = 28;

    public TrashScreen(TrashMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = TrashMenu.HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = TrashMenu.INVENTORY_Y - 11;
    }

    private int modeX() {
        return leftPos + imageWidth - 8 - MODE_W;
    }

    private int clearX() {
        return modeX() - 2 - BUTTON_H;
    }

    private int buttonY() {
        return topPos + TrashMenu.FILTER_Y - 15;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (inside(mouseX, mouseY, modeX(), buttonY(), MODE_W, BUTTON_H)) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable(menu.inventory().allowList() ? "gui.omnivault.trash.allow" : "gui.omnivault.trash.deny"),
                    Component.translatable(menu.inventory().allowList() ? "gui.omnivault.trash.allow.desc" : "gui.omnivault.trash.deny.desc")
                            .withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.translatable("gui.omnivault.trash.filter.empty").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, clearX(), buttonY(), BUTTON_H, BUTTON_H)) {
            g.renderTooltip(font, Component.translatable("gui.omnivault.trash.clear"), mouseX, mouseY);
        } else if (hoveredSlot == null && inside(mouseX, mouseY, leftPos + TrashMenu.INPUT_X - 4, topPos + TrashMenu.TOP_ROW_Y - 4, 24, 24)) {
            g.renderTooltip(font, Component.translatable("gui.omnivault.trash.input"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiStyle.window(g, x, y, imageWidth, imageHeight);

        // Top row: void slot -> vortex -> last voided.
        GuiStyle.panel(g, x + 7, y + 17, imageWidth - 14, 46);
        GuiStyle.bigSlot(g, x + TrashMenu.INPUT_X - 4, y + TrashMenu.TOP_ROW_Y - 4, 24, GuiStyle.VIOLET);
        GuiStyle.bigSlot(g, x + TrashMenu.RECOVERY_X - 4, y + TrashMenu.TOP_ROW_Y - 4, 24, GuiStyle.ACCENT);
        drawVortex(g, x + VORTEX_X, y + VORTEX_Y, x + TrashMenu.INPUT_X + 8, y + TrashMenu.TOP_ROW_Y + 8);
        drawArrow(g, x + TrashMenu.INPUT_X + 22, x + VORTEX_X - 3, y + TrashMenu.TOP_ROW_Y + 7, GuiStyle.VIOLET, false);
        drawArrow(g, x + VORTEX_X + VORTEX_SIZE + 3, x + TrashMenu.RECOVERY_X - 7, y + TrashMenu.TOP_ROW_Y + 7, GuiStyle.ACCENT_DIM, true);

        // Filter row.
        boolean modeHover = inside(mouseX, mouseY, modeX(), buttonY(), MODE_W, BUTTON_H);
        GuiStyle.button(g, modeX(), buttonY(), MODE_W, BUTTON_H, modeHover, false);
        boolean clearHover = inside(mouseX, mouseY, clearX(), buttonY(), BUTTON_H, BUTTON_H);
        GuiStyle.button(g, clearX(), buttonY(), BUTTON_H, BUTTON_H, clearHover, false);
        int cross = clearHover ? GuiStyle.BAD : GuiStyle.TEXT_DIM;
        for (int i = 0; i < 6; i++) {
            g.fill(clearX() + 3 + i, buttonY() + 3 + i, clearX() + 4 + i, buttonY() + 4 + i, cross);
            g.fill(clearX() + 8 - i, buttonY() + 3 + i, clearX() + 9 - i, buttonY() + 4 + i, cross);
        }
        GuiStyle.well(g, x + 6, y + TrashMenu.FILTER_Y - 2, TrashInventory.FILTER_SLOTS * 18 + 2, 20);
        GuiStyle.slots(g, x + 7, y + TrashMenu.FILTER_Y - 1, TrashInventory.FILTER_SLOTS, 1);

        GuiStyle.playerInventory(g, x + 8, y + TrashMenu.INVENTORY_Y);
    }

    private static void drawArrow(GuiGraphics g, int x0, int x1, int y, int color, boolean dashed) {
        for (int x = x0; x < x1 - 2; x++) {
            if (!dashed || ((x - x0) / 2) % 2 == 0) {
                g.fill(x, y, x + 1, y + 2, color);
            }
        }
        g.fill(x1 - 3, y - 2, x1 - 2, y + 4, color);
        g.fill(x1 - 2, y - 1, x1 - 1, y + 3, color);
        g.fill(x1 - 1, y, x1, y + 2, color);
    }

    /** A swirling violet singularity; items fly into it from the void slot for a moment after voiding. */
    private void drawVortex(GuiGraphics g, int x, int y, int inputCx, int inputCy) {
        GuiStyle.well(g, x, y, VORTEX_SIZE, VORTEX_SIZE);
        int cx = x + VORTEX_SIZE / 2;
        int cy = y + VORTEX_SIZE / 2;
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFF241038);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF07090D);
        float time = (Util.getMillis() % 100000L) / 1000f;
        for (int arm = 0; arm < 3; arm++) {
            for (int step = 0; step < 9; step++) {
                float radius = 11.5f - step * 1.15f;
                float angle = time * 2.6f + arm * Mth.TWO_PI / 3 + step * 0.42f;
                int px = cx + Math.round(Mth.cos(angle) * radius) - 1;
                int py = cy + Math.round(Mth.sin(angle) * radius) - 1;
                int color = step < 3 ? 0xFF3F1D63 : step < 6 ? 0xFF6A35A8 : 0xFFA46BFF;
                g.fill(px, py, px + 2, py + 2, color);
            }
        }
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFC9A6FF);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        GuiStyle.title(g, font, title, titleLabelX, titleLabelY);
        GuiStyle.centered(g, font, Component.translatable("gui.omnivault.trash.void"), TrashMenu.INPUT_X + 8, TrashMenu.TOP_ROW_Y + 22, GuiStyle.TEXT_DIM);
        GuiStyle.centered(g, font, Component.translatable("gui.omnivault.trash.last"), TrashMenu.RECOVERY_X + 8, TrashMenu.TOP_ROW_Y + 22, GuiStyle.TEXT_DIM);
        GuiStyle.labelDim(g, font, Component.translatable("gui.omnivault.trash.filter"), 8, TrashMenu.FILTER_Y - 12);
        GuiStyle.centered(g, font, Component.translatable(menu.inventory().allowList() ? "gui.omnivault.trash.mode.allow" : "gui.omnivault.trash.mode.deny"),
                modeX() - leftPos + MODE_W / 2, buttonY() - topPos + 2, menu.inventory().allowList() ? GuiStyle.GOOD : GuiStyle.WARN);
        GuiStyle.labelDim(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
        // Ghost filter entries read as markers rather than real items.
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        for (int i = 0; i < TrashInventory.FILTER_SLOTS; i++) {
            if (menu.slots.get(TrashMenu.FILTER_START + i).hasItem()) {
                int sx = 8 + i * 18;
                g.fill(sx, TrashMenu.FILTER_Y, sx + 16, TrashMenu.FILTER_Y + 16, 0x401B2029);
                g.fill(sx, TrashMenu.FILTER_Y + 15, sx + 16, TrashMenu.FILTER_Y + 16, menu.inventory().allowList() ? 0xC05BE08A : 0xC0F2C14E);
            }
        }
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int id = -1;
            if (inside(mouseX, mouseY, modeX(), buttonY(), MODE_W, BUTTON_H)) {
                id = TrashMenu.BUTTON_MODE;
            } else if (inside(mouseX, mouseY, clearX(), buttonY(), BUTTON_H, BUTTON_H)) {
                id = TrashMenu.BUTTON_CLEAR;
            }
            if (id >= 0) {
                menu.clickMenuButton(minecraft.player, id);
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
