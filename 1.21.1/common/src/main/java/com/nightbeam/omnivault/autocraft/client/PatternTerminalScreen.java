package com.nightbeam.omnivault.autocraft.client;

import com.nightbeam.omnivault.autocraft.menu.PatternTerminalMenu;
import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.client.gui.OVButton;
import com.nightbeam.omnivault.network.client.TerminalScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The network grid plus the pattern workbench: ghost recipe, mode, blank and encoded pattern slots. */
public class PatternTerminalScreen extends TerminalScreen<PatternTerminalMenu> {
    private static final int ENCODE_X = 131;

    public PatternTerminalScreen(PatternTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int top = topPos + menu.craftTop();
        addRenderableWidget(new OVButton(leftPos + 8, top, 12, 12, () -> Component.literal(menu.processing() ? "P" : "C"),
                () -> Component.translatable(menu.processing() ? "gui.omnivault.pattern.mode.processing" : "gui.omnivault.pattern.mode.crafting"),
                () -> click(PatternTerminalMenu.BUTTON_MODE)));
        addRenderableWidget(new OVButton(leftPos + 8, top + 14, 12, 12, () -> Component.literal("×"),
                () -> Component.translatable("gui.omnivault.pattern.clear"),
                () -> click(PatternTerminalMenu.BUTTON_CLEAR)));
        addRenderableWidget(new OVButton(leftPos + ENCODE_X, top + 18, 18, 18, () -> Component.literal("»"),
                () -> Component.translatable("gui.omnivault.pattern.encode"),
                () -> click(PatternTerminalMenu.BUTTON_ENCODE)));
        addRenderableWidget(new OVButton(leftPos - 20, topPos + 80, 18, 18, () -> Component.literal("⚙"),
                () -> Component.translatable("gui.omnivault.pattern.status"),
                () -> click(PatternTerminalMenu.BUTTON_STATUS)));
    }

    private void click(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderExtraBackground(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = topPos + menu.craftTop();
        GuiStyle.panel(graphics, leftPos + 4, top - 4, 172, 3 * 18 + 8);
        GuiStyle.slots(graphics, leftPos + PatternTerminalMenu.CRAFT_X - 1, top - 1, 3, 3);
        // Arrow from the recipe to its outputs.
        int ax = leftPos + 84;
        int ay = top + 22;
        int glow = menu.processing() ? GuiStyle.VIOLET : GuiStyle.ACCENT;
        graphics.fill(ax, ay + 3, ax + 13, ay + 7, GuiStyle.darken(glow));
        for (int i = 0; i < 6; i++) {
            graphics.fill(ax + 13 + i, ay - 1 + i, ax + 14 + i, ay + 11 - i, glow);
        }
        if (menu.processing()) {
            GuiStyle.slots(graphics, leftPos + PatternTerminalMenu.OUTPUT_X - 1, top - 1, 1, 3);
        } else {
            GuiStyle.bigSlot(graphics, leftPos + PatternTerminalMenu.OUTPUT_X - 5, top + 13, 26, GuiStyle.ACCENT);
        }
        // Blank pattern in (top), encoded pattern out (bottom), joined by a violet trace.
        GuiStyle.slot(graphics, leftPos + PatternTerminalMenu.PATTERN_X - 1, top - 1);
        int tx = leftPos + PatternTerminalMenu.PATTERN_X + 7;
        graphics.fill(tx, top + 17, tx + 2, top + 35, GuiStyle.darken(GuiStyle.VIOLET));
        GuiStyle.bigSlot(graphics, leftPos + PatternTerminalMenu.PATTERN_X - 1, top + 35, 18, GuiStyle.VIOLET);
        GuiStyle.slot(graphics, leftPos + PatternTerminalMenu.PATTERN_X - 1, top + 35);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.processing() && scrollY != 0) {
            for (int i = 0; i < menu.ghosts().size(); i++) {
                PatternTerminalMenu.PatternGhostSlot ghost = menu.ghosts().get(i);
                if (ghost.isActive() && ghost.hasItem() && isHovering(ghost.x, ghost.y, 16, 16, mouseX, mouseY)) {
                    click(PatternTerminalMenu.BUTTON_ADJUST + i * 2 + (scrollY < 0 ? 1 : 0));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
