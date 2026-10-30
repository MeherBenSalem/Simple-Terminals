package com.nightbeam.omnivault.autocraft.client;

import com.nightbeam.omnivault.autocraft.menu.AssemblerMenu;
import com.nightbeam.omnivault.client.gui.GuiStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {
    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = AssemblerMenu.PLAYER_Y + 82;
        inventoryLabelY = AssemblerMenu.PLAYER_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiStyle.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        GuiStyle.panel(graphics, leftPos + AssemblerMenu.UPGRADE_X - 5, topPos + 4, 26, 80);
        GuiStyle.slots(graphics, leftPos + AssemblerMenu.UPGRADE_X - 1, topPos + 7, 1, 4);
        // The fabrication chamber: a pulsing core while working.
        int cx = leftPos + 30;
        int cy = topPos + 48;
        GuiStyle.well(graphics, cx - 18, cy - 18, 36, 36);
        float time = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;
        float pulse = menu.working() ? 0.6f + 0.4f * Mth.sin(time * 0.3f) : 0.25f;
        int core = menu.online() ? GuiStyle.VIOLET : GuiStyle.PANEL_HIGHLIGHT;
        int size = menu.working() ? 6 + Math.round(3 * pulse) : 6;
        graphics.fill(cx - size - 2, cy - size - 2, cx + size + 2, cy + size + 2, GuiStyle.darken(core) & 0x80FFFFFF | 0x40000000);
        graphics.fill(cx - size, cy - size, cx + size, cy + size, core);
        if (menu.working()) {
            graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFF0E6FF);
            for (int i = 0; i < 4; i++) {
                int angle = (int) (time * 6) + i * 90;
                int ox = Math.round(Mth.cos(angle * Mth.DEG_TO_RAD) * 14);
                int oy = Math.round(Mth.sin(angle * Mth.DEG_TO_RAD) * 14);
                graphics.fill(cx + ox - 1, cy + oy - 1, cx + ox + 1, cy + oy + 1, GuiStyle.ACCENT);
            }
        }
        GuiStyle.playerInventory(graphics, leftPos + 8, topPos + AssemblerMenu.PLAYER_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiStyle.title(graphics, font, title, 8, 6);
        GuiStyle.labelDim(graphics, font, playerInventoryTitle, 8, inventoryLabelY);
        Component state = Component.translatable(!menu.online() ? "gui.omnivault.assembler.offline"
                : menu.working() ? "gui.omnivault.assembler.working" : "gui.omnivault.assembler.idle");
        graphics.drawString(font, state, 58, 24, !menu.online() ? GuiStyle.BAD : menu.working() ? GuiStyle.GOOD : GuiStyle.TEXT_DIM, false);
        GuiStyle.label(graphics, font, Component.translatable("gui.omnivault.assembler.speed", menu.craftsPerCycle()), 58, 38);
        GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.assembler.cycle", menu.interval()), 58, 48);
        graphics.drawWordWrap(font, Component.translatable("gui.omnivault.assembler.upgrades"), 58, 62, 86, GuiStyle.TEXT_DIM);
    }
}
