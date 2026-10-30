package com.nightbeam.omnivault.autocraft.client;

import com.nightbeam.omnivault.autocraft.AutocraftPackets;
import com.nightbeam.omnivault.autocraft.job.CraftingJob;
import com.nightbeam.omnivault.autocraft.menu.CraftingStatusMenu;
import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.client.gui.OVButton;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.util.Formatting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every job on the network with a progress bar, what it waits for, and a cancel button. */
public class CraftingStatusScreen extends AbstractContainerScreen<CraftingStatusMenu> {
    private static final int LIST_X = 7;
    private static final int LIST_Y = 20;
    private static final int ROW_H = 26;
    private static final int ROWS = 6;
    private static final int LIST_W = 166;
    private static final int SCROLL_X = LIST_X + LIST_W + 3;

    private final List<OVButton> cancelButtons = new ArrayList<>();
    private int scrollRow;

    public CraftingStatusScreen(CraftingStatusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 194;
        imageHeight = LIST_Y + ROWS * ROW_H + 30;
    }

    @Override
    protected void init() {
        super.init();
        cancelButtons.clear();
        for (int row = 0; row < ROWS; row++) {
            int index = row;
            OVButton button = new OVButton(leftPos + LIST_X + LIST_W - 15, topPos + LIST_Y + row * ROW_H + 7, 12, 12,
                    () -> Component.literal("✕"), () -> Component.translatable("gui.omnivault.status.cancel"), () -> cancel(index));
            cancelButtons.add(addRenderableWidget(button));
        }
        if (menu.canGoBack()) {
            addRenderableWidget(new OVButton(leftPos + imageWidth - 64, topPos + imageHeight - 20, 56, 14,
                    () -> Component.translatable("gui.omnivault.craft.back"), () -> Component.translatable("gui.omnivault.status.back"),
                    () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CraftingStatusMenu.BUTTON_BACK)));
        }
    }

    private void cancel(int row) {
        int index = scrollRow + row;
        if (index < menu.jobs().size()) {
            Services.PLATFORM.sendToServer(new AutocraftPackets.CancelJob(menu.containerId, menu.jobs().get(index).id()));
        }
    }

    private int maxScroll() {
        return Math.max(0, menu.jobs().size() - ROWS);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
        for (int row = 0; row < ROWS; row++) {
            int index = scrollRow + row;
            boolean present = index < menu.jobs().size();
            OVButton button = cancelButtons.get(row);
            button.visible = present;
            button.active = present && menu.jobs().get(index).statusValue() != CraftingJob.Status.RETURNING;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderJobs(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiStyle.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        GuiStyle.well(graphics, leftPos + LIST_X - 1, topPos + LIST_Y - 1, LIST_W + 2, ROWS * ROW_H + 2);
        GuiStyle.scrollbar(graphics, leftPos + SCROLL_X, topPos + LIST_Y - 1, ROWS * ROW_H + 2,
                maxScroll() == 0 ? 0 : scrollRow / (float) maxScroll(), maxScroll() > 0);
    }

    private void renderJobs(GuiGraphics graphics, int mouseX, int mouseY) {
        AutocraftPackets.JobView hovered = null;
        for (int row = 0; row < ROWS; row++) {
            int index = scrollRow + row;
            if (index >= menu.jobs().size()) {
                break;
            }
            AutocraftPackets.JobView job = menu.jobs().get(index);
            int x = leftPos + LIST_X;
            int y = topPos + LIST_Y + row * ROW_H;
            if (row % 2 == 1) {
                graphics.fill(x, y, x + LIST_W, y + ROW_H, 0x10FFFFFF);
            }
            GuiStyle.slot(graphics, x + 2, y + 4);
            graphics.renderItem(job.target().view(), x + 3, y + 5);
            Component name = Component.translatable("gui.omnivault.status.job", Formatting.compact(job.amount()), job.target().view().getHoverName());
            graphics.drawString(font, font.substrByWidth(name, LIST_W - 42).getString(), x + 24, y + 3, GuiStyle.TEXT, false);
            CraftingJob.Status status = job.statusValue();
            graphics.drawString(font, statusText(status), x + 24, y + 14, statusColor(status), false);
            float progress = job.total() == 0 ? 1 : job.done() / (float) job.total();
            GuiStyle.bar(graphics, x + LIST_W - 58, y + 15, 40, 6, progress, status == CraftingJob.Status.RETURNING ? GuiStyle.GOOD : GuiStyle.ACCENT);
            if (mouseX >= x && mouseX < x + LIST_W - 16 && mouseY >= y && mouseY < y + ROW_H) {
                hovered = job;
            }
        }
        if (menu.jobs().isEmpty()) {
            Component empty = Component.translatable(!menu.received() ? "gui.omnivault.core.loading"
                    : menu.processorCount() == 0 ? "gui.omnivault.status.no_processor" : "gui.omnivault.status.empty");
            GuiStyle.centered(graphics, font, empty, leftPos + LIST_X + LIST_W / 2, topPos + LIST_Y + ROWS * ROW_H / 2 - 4,
                    menu.received() && menu.processorCount() == 0 ? GuiStyle.BAD : GuiStyle.TEXT_DIM);
        }
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.omnivault.status.job", Formatting.full(hovered.amount()), hovered.target().view().getHoverName()));
            lines.add(Component.translatable("gui.omnivault.status.progress", Formatting.full(hovered.done()), Formatting.full(hovered.total()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.omnivault.status.state." + hovered.statusValue().name().toLowerCase(Locale.ROOT) + ".long")
                    .withStyle(ChatFormatting.DARK_AQUA));
            if (!hovered.awaiting().isEmpty()) {
                lines.add(Component.translatable("gui.omnivault.status.awaiting").withStyle(ChatFormatting.LIGHT_PURPLE));
                for (AutocraftPackets.Amount amount : hovered.awaiting()) {
                    lines.add(Component.translatable("tooltip.omnivault.pattern.entry", Formatting.full(amount.amount()),
                            amount.key().view().getHoverName()).withStyle(ChatFormatting.GRAY));
                }
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private static Component statusText(CraftingJob.Status status) {
        return Component.translatable("gui.omnivault.status.state." + status.name().toLowerCase(Locale.ROOT));
    }

    private static int statusColor(CraftingJob.Status status) {
        return switch (status) {
            case CRAFTING -> GuiStyle.ACCENT;
            case WAITING -> GuiStyle.VIOLET;
            case RETURNING -> GuiStyle.GOOD;
            default -> GuiStyle.WARN;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiStyle.title(graphics, font, title, 8, 6);
        if (menu.received()) {
            GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.status.processors", menu.jobs().size(), menu.processorCount()),
                    8, imageHeight - 17);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
