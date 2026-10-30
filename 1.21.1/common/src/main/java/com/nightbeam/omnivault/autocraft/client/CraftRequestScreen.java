package com.nightbeam.omnivault.autocraft.client;

import com.nightbeam.omnivault.autocraft.AutocraftPackets;
import com.nightbeam.omnivault.autocraft.menu.CraftRequestMenu;
import com.nightbeam.omnivault.autocraft.plan.PlanError;
import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.client.gui.OVButton;
import com.nightbeam.omnivault.network.NetworkPackets;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.util.Formatting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Pick an amount (+1/+10/+64, or type it), Calculate to see what the job uses from storage, what
 * it crafts and what is missing, then Start. Start stays disabled until a plan for exactly this
 * amount has nothing missing.
 */
public class CraftRequestScreen extends AbstractContainerScreen<CraftRequestMenu> {
    private static final int LIST_X = 8;
    private static final int LIST_Y = 86;
    private static final int COLUMNS = 2;
    private static final int ROWS = 5;
    private static final int CELL_W = 82;
    private static final int CELL_H = 22;
    private static final int SCROLL_X = LIST_X + COLUMNS * CELL_W + 4;

    private EditBox amount;
    private OVButton start;
    private int scrollRow;
    private boolean sentInitial;
    /** The full status text while the line under the plan shows it cut short, for its tooltip. */
    private Component clippedStatus;

    public CraftRequestScreen(CraftRequestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 194;
        imageHeight = 232;
    }

    @Override
    protected void init() {
        super.init();
        amount = new EditBox(font, leftPos + 12, topPos + 43, 72, 10, Component.translatable("gui.omnivault.craft.amount"));
        amount.setBordered(false);
        amount.setMaxLength(10);
        amount.setTextColor(GuiStyle.TEXT);
        amount.setFilter(text -> text.isEmpty() || text.matches("\\d{1,10}"));
        amount.setValue(menu.plan() != null ? String.valueOf(menu.plan().amount()) : "1");
        addRenderableWidget(amount);
        setInitialFocus(amount);
        int[] steps = {1, 10, 64};
        for (int i = 0; i < steps.length; i++) {
            int step = steps[i];
            addRenderableWidget(new OVButton(leftPos + 92 + i * 32, topPos + 39, 30, 14, () -> Component.literal("+" + step),
                    () -> Component.translatable("gui.omnivault.craft.add", step), () -> change(step)));
            addRenderableWidget(new OVButton(leftPos + 92 + i * 32, topPos + 57, 30, 14, () -> Component.literal("-" + step),
                    () -> Component.translatable("gui.omnivault.craft.remove", step), () -> change(-step)));
        }
        addRenderableWidget(new OVButton(leftPos + 8, topPos + 57, 80, 14, () -> Component.translatable("gui.omnivault.craft.calculate"),
                () -> Component.translatable("gui.omnivault.craft.calculate.tooltip"), this::calculate));
        start = addRenderableWidget(new OVButton(leftPos + imageWidth - 64, topPos + imageHeight - 20, 56, 14,
                () -> Component.translatable("gui.omnivault.craft.start"),
                () -> Component.translatable(canStart() ? "gui.omnivault.craft.start.tooltip" : "gui.omnivault.craft.start.blocked"),
                this::startJob));
        if (menu.canGoBack()) {
            addRenderableWidget(new OVButton(leftPos + 8, topPos + imageHeight - 20, 56, 14, () -> Component.translatable("gui.omnivault.craft.back"),
                    () -> Component.translatable("gui.omnivault.craft.back.tooltip"),
                    () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CraftRequestMenu.BUTTON_BACK)));
        }
        if (!sentInitial) {
            sentInitial = true;
            calculate();
        }
    }

    private long value() {
        String text = amount.getValue();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void change(int delta) {
        long next = Mth.clamp(value() + delta, 1, 999_999_999L);
        amount.setValue(String.valueOf(next));
    }

    private void calculate() {
        long value = value();
        if (value > 0) {
            Services.PLATFORM.sendToServer(new NetworkPackets.MenuValue(menu.containerId, CraftRequestMenu.VALUE_CALCULATE, value));
        }
    }

    private boolean planIsCurrent() {
        return menu.plan() != null && menu.plan().amount() == value();
    }

    private boolean canStart() {
        return planIsCurrent() && menu.plan().canStart();
    }

    private void startJob() {
        if (canStart()) {
            Services.PLATFORM.sendToServer(new NetworkPackets.MenuValue(menu.containerId, CraftRequestMenu.VALUE_START, value()));
        }
    }

    // -- rendering ---------------------------------------------------------------------------------

    private List<AutocraftPackets.PlanEntry> entries() {
        return menu.plan() == null ? List.of() : menu.plan().entries();
    }

    private int maxScroll() {
        int rows = (entries().size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows - ROWS);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        start.active = canStart();
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
        super.render(graphics, mouseX, mouseY, partialTick);
        renderEntries(graphics, mouseX, mouseY);
        if (amount.getValue().isEmpty() && !amount.isFocused()) {
            graphics.drawString(font, Component.translatable("gui.omnivault.craft.amount"), leftPos + 12, topPos + 43, GuiStyle.TEXT_DIM, false);
        }
        renderTooltip(graphics, mouseX, mouseY);
        int statusY = topPos + LIST_Y + ROWS * CELL_H + 5;
        if (clippedStatus != null && mouseX >= leftPos + 8 && mouseX < leftPos + imageWidth - 8 && mouseY >= statusY && mouseY < statusY + 9) {
            graphics.renderTooltip(font, clippedStatus, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiStyle.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        GuiStyle.bigSlot(graphics, leftPos + 8, topPos + 17, 18, GuiStyle.VIOLET);
        graphics.renderItem(menu.key().view(), leftPos + 9, topPos + 18);
        GuiStyle.well(graphics, leftPos + 8, topPos + 39, 80, 14);
        GuiStyle.well(graphics, leftPos + LIST_X - 1, topPos + LIST_Y - 1, COLUMNS * CELL_W + 2, ROWS * CELL_H + 2);
        GuiStyle.scrollbar(graphics, leftPos + SCROLL_X, topPos + LIST_Y - 1, ROWS * CELL_H + 2,
                maxScroll() == 0 ? 0 : scrollRow / (float) maxScroll(), maxScroll() > 0);
    }

    private void renderEntries(GuiGraphics graphics, int mouseX, int mouseY) {
        List<AutocraftPackets.PlanEntry> entries = entries();
        AutocraftPackets.PlanEntry hovered = null;
        for (int i = 0; i < ROWS * COLUMNS; i++) {
            int index = scrollRow * COLUMNS + i;
            if (index >= entries.size()) {
                break;
            }
            AutocraftPackets.PlanEntry entry = entries.get(index);
            int x = leftPos + LIST_X + (i % COLUMNS) * CELL_W;
            int y = topPos + LIST_Y + (i / COLUMNS) * CELL_H;
            int tint = entry.missing() > 0 ? 0x40FF6B6B : entry.crafted() > 0 ? 0x2038E1FF : 0x10FFFFFF;
            graphics.fill(x + 1, y + 1, x + CELL_W - 1, y + CELL_H - 1, tint);
            graphics.renderItem(entry.key().view(), x + 3, y + 3);
            List<Component> lines = new ArrayList<>(2);
            if (entry.missing() > 0) {
                lines.add(Component.translatable("gui.omnivault.craft.cell.missing", Formatting.compact(entry.missing())).withColor(GuiStyle.BAD));
            }
            if (entry.crafted() > 0) {
                lines.add(Component.translatable("gui.omnivault.craft.cell.craft", Formatting.compact(entry.crafted())).withColor(GuiStyle.ACCENT));
            }
            if (entry.used() > 0) {
                lines.add(Component.translatable("gui.omnivault.craft.cell.use", Formatting.compact(entry.used())).withColor(GuiStyle.TEXT_DIM));
            }
            for (int line = 0; line < Math.min(2, lines.size()); line++) {
                graphics.drawString(font, lines.get(line), x + 22, y + 2 + line * 10, 0xFFFFFFFF, false);
            }
            if (mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H) {
                graphics.fill(x, y, x + CELL_W, y + CELL_H, 0x2038E1FF);
                hovered = entry;
            }
        }
        if (hovered != null) {
            List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hovered.key().view()));
            if (hovered.used() > 0) {
                tooltip.add(Component.translatable("gui.omnivault.craft.tip.used", Formatting.full(hovered.used())).withStyle(ChatFormatting.GRAY));
            }
            if (hovered.crafted() > 0) {
                tooltip.add(Component.translatable("gui.omnivault.craft.tip.crafted", Formatting.full(hovered.crafted())).withStyle(ChatFormatting.AQUA));
            }
            if (hovered.missing() > 0) {
                tooltip.add(Component.translatable("gui.omnivault.craft.tip.missing", Formatting.full(hovered.missing())).withStyle(ChatFormatting.RED));
            }
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiStyle.title(graphics, font, title, 8, 6);
        graphics.drawString(font, font.plainSubstrByWidth(menu.key().view().getHoverName().getString(), imageWidth - 40), 32, 19, GuiStyle.TEXT, false);
        GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.craft.how_many"), 32, 28);
        AutocraftPackets.PlanView plan = menu.plan();
        GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.craft.plan"), LIST_X, LIST_Y - 11);
        if (plan != null && planIsCurrent() && plan.steps() > 0) {
            Component summary = Component.translatable("gui.omnivault.craft.summary", plan.steps(), Formatting.compact(plan.crafts()));
            graphics.drawString(font, summary, SCROLL_X + 8 - font.width(summary), LIST_Y - 11, GuiStyle.TEXT_DIM, false);
        }
        if (plan == null) {
            GuiStyle.centered(graphics, font, Component.translatable("gui.omnivault.craft.calculating"), imageWidth / 2, LIST_Y + ROWS * CELL_H / 2 - 4, GuiStyle.TEXT_DIM);
        }
        int statusY = LIST_Y + ROWS * CELL_H + 5;
        Component status;
        int color;
        if (plan == null) {
            status = Component.empty();
            color = GuiStyle.TEXT_DIM;
        } else if (!planIsCurrent()) {
            status = Component.translatable("gui.omnivault.craft.stale");
            color = GuiStyle.WARN;
        } else if (plan.error() >= 0) {
            PlanError error = PlanError.values()[Math.floorMod(plan.error(), PlanError.values().length)];
            status = error == PlanError.PROCESSORS_BUSY
                    ? Component.translatable("gui.omnivault.craft.error.processors_busy", plan.processors())
                    : error.message();
            color = GuiStyle.BAD;
        } else if (plan.noAssembler()) {
            status = Component.translatable("gui.omnivault.craft.warn.no_assembler");
            color = GuiStyle.WARN;
        } else {
            status = Component.translatable("gui.omnivault.craft.ready", plan.freeProcessors(), plan.processors());
            color = GuiStyle.GOOD;
        }
        // One line only: the Back and Start buttons sit right below it.
        Component shown = GuiStyle.fit(font, status, imageWidth - 16);
        clippedStatus = shown == status ? null : status;
        graphics.drawString(font, shown, 8, statusY, color, false);
    }

    // -- input -------------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (canStart()) {
                startJob();
            } else {
                calculate();
            }
            return true;
        }
        if (amount.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            amount.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && mouseX >= leftPos + LIST_X && mouseX < leftPos + SCROLL_X + 8 && mouseY >= topPos + LIST_Y
                && mouseY < topPos + LIST_Y + ROWS * CELL_H) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
