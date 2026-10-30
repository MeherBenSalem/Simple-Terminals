package com.nightbeam.omnivault.backpack.client;

import com.nightbeam.omnivault.backpack.BackpackLayout;
import com.nightbeam.omnivault.backpack.BackpackMenu;
import com.nightbeam.omnivault.backpack.BackpackPayloads;
import com.nightbeam.omnivault.backpack.upgrade.BackpackUpgradeItem;
import com.nightbeam.omnivault.backpack.upgrade.BackpackUpgrades;
import com.nightbeam.omnivault.backpack.upgrade.UpgradeConfig;
import com.nightbeam.omnivault.backpack.upgrade.UpgradeType;
import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.util.InventorySorting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The backpack screen: a (scrolling) slot grid, an upgrade bay on the left, and one tab per configurable
 * upgrade on the right. Tabs expand into panels with the upgrade's filter and settings, or the crafting grid.
 */
public class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {
    private static final int TAB_ICON_INSET = 5;
    private static final int FILTER_DX = 8;
    private static final int FILTER_DY = 22;
    private static final int CONTROLS_DX = 68;
    private static final int FILTER_PANEL_HEIGHT = FILTER_DY + 3 * BackpackLayout.SLOT + 8;

    private final BackpackLayout layout;
    /** Upgrade slot whose tab is expanded, or -1. */
    private int openUpgradeSlot = -1;
    private boolean draggingScrollbar;
    /** Row requested from the server but not confirmed yet, or -1. */
    private int pendingRow = -1;

    public BackpackScreen(BackpackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.layout = menu.layout();
        this.imageWidth = layout.imageWidth();
        this.imageHeight = layout.imageHeight();
        this.titleLabelX = 18;
        this.titleLabelY = 6;
        this.inventoryLabelX = layout.inventoryX() - 1;
        this.inventoryLabelY = layout.inventoryY() - 11;
    }

    // ---------------------------------------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------------------------------------

    private record Tab(int upgradeSlot, UpgradeType type, ItemStack card, int x, int y, int width, int height, boolean open) {
        boolean headerHit(double mx, double my) {
            return mx >= x && mx < x + (open ? width : BackpackLayout.TAB_WIDTH) && my >= y && my < y + (open ? 20 : BackpackLayout.TAB_HEIGHT);
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    /** Tabs in screen coordinates; the crafting tab always comes first so its slots never move. */
    private List<Tab> tabs() {
        Container upgrades = menu.upgradeContainer();
        List<Integer> order = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < upgrades.getContainerSize(); i++) {
                if (upgrades.getItem(i).getItem() instanceof BackpackUpgradeItem item && item.type().hasTab()
                        && (item.type() == UpgradeType.CRAFTING) == (pass == 0)) {
                    order.add(i);
                }
            }
        }
        if (!order.contains(openUpgradeSlot)) {
            openUpgradeSlot = -1;
        }
        List<Tab> tabs = new ArrayList<>();
        int x = leftPos + layout.panelX();
        int y = topPos + BackpackLayout.TAB_Y;
        for (int slot : order) {
            ItemStack card = upgrades.getItem(slot);
            UpgradeType type = ((BackpackUpgradeItem) card.getItem()).type();
            if (slot == openUpgradeSlot) {
                int height = type == UpgradeType.CRAFTING ? BackpackLayout.CRAFT_PANEL_HEIGHT : FILTER_PANEL_HEIGHT;
                tabs.add(new Tab(slot, type, card, x, y - 2, BackpackLayout.PANEL_WIDTH, height, true));
                y += height + 2;
            } else {
                tabs.add(new Tab(slot, type, card, x, y, BackpackLayout.TAB_WIDTH, BackpackLayout.TAB_HEIGHT, false));
                y += BackpackLayout.TAB_SPACING;
            }
        }
        menu.craftingTabOpen = tabs.stream().anyMatch(t -> t.open() && t.type() == UpgradeType.CRAFTING && t.y() == topPos + BackpackLayout.PANEL_Y);
        return tabs;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        tabs();
        if (pendingRow >= 0 && pendingRow == menu.scrollRow()) {
            pendingRow = -1;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderExtraTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        List<Tab> tabs = tabs();
        renderBay(g);
        for (Tab tab : tabs) {
            renderTabFrame(g, tab, mouseX, mouseY);
        }
        GuiStyle.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Tab tab : tabs) {
            renderTabContents(g, tab, mouseX, mouseY);
        }
        renderGrid(g);
        renderHeader(g, mouseX, mouseY);
        GuiStyle.playerInventory(g, leftPos + layout.inventoryX(), topPos + layout.inventoryY());
        int locked = menu.lockedMenuSlot();
        if (locked >= 0) {
            Slot slot = menu.getSlot(locked);
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF3F1D63);
            g.fill(x, y, x + 16, y + 16, 0xFF241038);
            g.renderOutline(x - 1, y - 1, 18, 18, 0xFFA46BFF);
        }
    }

    private void renderBay(GuiGraphics g) {
        int x = leftPos - BackpackLayout.BAY_WIDTH;
        int y = topPos + 4;
        int w = BackpackLayout.BAY_WIDTH + 4;
        int h = layout.bayHeight();
        GuiStyle.window(g, x, y, w, h);
        // A small card glyph as the bay's label.
        int cx = x + 8;
        int cy = y + 6;
        g.fill(cx, cy, cx + 10, cy + 8, GuiStyle.OUTLINE);
        g.fill(cx + 1, cy + 1, cx + 9, cy + 7, GuiStyle.PANEL_HIGHLIGHT);
        g.fill(cx + 1, cy + 1, cx + 9, cy + 2, GuiStyle.ACCENT);
        g.fill(cx + 3, cy + 3, cx + 7, cy + 5, GuiStyle.VIOLET);
        g.fill(cx + 12, cy + 2, cx + 15, cy + 3, GuiStyle.ACCENT_DIM);
        g.fill(cx + 12, cy + 4, cx + 14, cy + 5, GuiStyle.ACCENT_DIM);
        for (int i = 0; i < layout.upgradeSlots(); i++) {
            int sx = leftPos + BackpackLayout.BAY_SLOT_X - 1;
            int sy = topPos + BackpackLayout.BAY_SLOT_Y - 1 + i * BackpackLayout.SLOT;
            GuiStyle.slot(g, sx, sy);
            if (menu.upgradeContainer().getItem(i).isEmpty()) {
                // Empty-card silhouette.
                g.fill(sx + 5, sy + 4, sx + 13, sy + 14, 0xFF2F3642);
                g.fill(sx + 6, sy + 5, sx + 12, sy + 6, 0xFF3E4757);
                g.fill(sx + 7, sy + 8, sx + 11, sy + 11, 0xFF3E4757);
            }
        }
    }

    private void renderTabFrame(GuiGraphics g, Tab tab, int mouseX, int mouseY) {
        int accent = 0xFF000000 | tab.type().color();
        if (tab.open()) {
            GuiStyle.window(g, tab.x(), tab.y(), tab.width(), tab.height());
            g.fill(tab.x() + 3, tab.y() + 2, tab.x() + tab.width() - 3, tab.y() + 3, accent);
        } else {
            boolean hovered = tab.contains(mouseX, mouseY);
            g.fill(tab.x(), tab.y(), tab.x() + tab.width(), tab.y() + tab.height(), GuiStyle.OUTLINE);
            g.fill(tab.x() + 1, tab.y() + 1, tab.x() + tab.width() - 1, tab.y() + tab.height() - 1, hovered ? GuiStyle.PANEL_LIGHT : GuiStyle.PANEL);
            g.fill(tab.x() + 1, tab.y() + 1, tab.x() + tab.width() - 1, tab.y() + 2, GuiStyle.PANEL_HIGHLIGHT);
            g.fill(tab.x() + tab.width() - 3, tab.y() + 2, tab.x() + tab.width() - 1, tab.y() + tab.height() - 2, accent);
        }
    }

    private void renderTabContents(GuiGraphics g, Tab tab, int mouseX, int mouseY) {
        int iconX = tab.x() + TAB_ICON_INSET + (tab.open() ? 0 : 1);
        int iconY = tab.y() + 4;
        g.renderItem(tab.card(), iconX, iconY);
        UpgradeConfig config = BackpackUpgradeItem.config(tab.card());
        if (!config.enabled()) {
            g.fill(iconX, iconY, iconX + 16, iconY + 16, 0x90101010);
        }
        if (!tab.open()) {
            return;
        }
        Component name = Component.translatable("gui.omnivault.backpack.tab." + tab.type().getSerializedName());
        String text = font.plainSubstrByWidth(name.getString(), tab.width() - 46);
        g.drawString(font, text, tab.x() + 24, tab.y() + 8, GuiStyle.TEXT, false);
        // Power toggle in the header.
        int px = tab.x() + tab.width() - 19;
        int py = tab.y() + 5;
        boolean hover = mouseX >= px && mouseX < px + 13 && mouseY >= py && mouseY < py + 13;
        GuiStyle.button(g, px, py, 13, 13, hover, false);
        int led = config.enabled() ? GuiStyle.GOOD : GuiStyle.BAD;
        g.fill(px + 4, py + 4, px + 9, py + 9, GuiStyle.OUTLINE);
        g.fill(px + 5, py + 5, px + 8, py + 8, led);

        if (tab.type() == UpgradeType.CRAFTING) {
            renderCraftingPanel(g, tab);
        } else {
            renderFilterPanel(g, tab, config, mouseX, mouseY);
        }
    }

    private void renderCraftingPanel(GuiGraphics g, Tab tab) {
        int gx = tab.x() + BackpackLayout.CRAFT_GRID_DX - 1;
        int gy = topPos + BackpackLayout.PANEL_Y + BackpackLayout.CRAFT_GRID_DY - 1;
        GuiStyle.well(g, gx - 2, gy - 2, 3 * BackpackLayout.SLOT + 4, 3 * BackpackLayout.SLOT + 4);
        GuiStyle.slots(g, gx, gy, 3, 3);
        int rx = tab.x() + BackpackLayout.CRAFT_RESULT_DX - 1;
        int ry = topPos + BackpackLayout.PANEL_Y + BackpackLayout.CRAFT_RESULT_DY - 1;
        // Arrow from the grid to the result.
        int ax = gx + 3 * BackpackLayout.SLOT + 5;
        int ay = ry + 7;
        g.fill(ax, ay, ax + 14, ay + 3, GuiStyle.ACCENT_DIM);
        g.fill(ax + 14, ay - 2, ax + 16, ay + 5, GuiStyle.ACCENT_DIM);
        g.fill(ax + 16, ay - 1, ax + 17, ay + 4, GuiStyle.ACCENT_DIM);
        g.fill(ax + 17, ay, ax + 18, ay + 3, GuiStyle.ACCENT_DIM);
        GuiStyle.bigSlot(g, rx - 3, ry - 3, 24, GuiStyle.ACCENT);
    }

    private void renderFilterPanel(GuiGraphics g, Tab tab, UpgradeConfig config, int mouseX, int mouseY) {
        int fx = tab.x() + FILTER_DX;
        int fy = tab.y() + FILTER_DY;
        if (tab.type().hasFilter()) {
            GuiStyle.well(g, fx - 2, fy - 2, 3 * BackpackLayout.SLOT + 4, 3 * BackpackLayout.SLOT + 4);
            for (int i = 0; i < UpgradeConfig.FILTER_SIZE; i++) {
                int sx = fx + (i % 3) * BackpackLayout.SLOT;
                int sy = fy + (i / 3) * BackpackLayout.SLOT;
                GuiStyle.slot(g, sx, sy);
                ItemStack ghost = config.filter().get(i);
                if (!ghost.isEmpty()) {
                    g.renderFakeItem(ghost, sx + 1, sy + 1);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x3A11151C);
                    g.pose().popPose();
                }
                if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 210);
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, GuiStyle.SLOT_HOVER);
                    g.pose().popPose();
                }
            }
        }
        for (Control control : controls(tab, config)) {
            boolean hover = control.contains(mouseX, mouseY);
            GuiStyle.button(g, control.x, control.y, control.w, control.h, hover, control.pressed);
            if (!control.label.getString().isEmpty()) {
                GuiStyle.centered(g, font, control.label, control.x + control.w / 2, control.y + (control.h - 8) / 2 + 1, control.color);
            }
        }
        if (tab.type().hasRange()) {
            int range = BackpackUpgrades.range(config);
            Component value = Component.translatable("gui.omnivault.backpack.range_value", range);
            GuiStyle.centered(g, font, value, tab.x() + CONTROLS_DX + 22, tab.y() + FILTER_DY + 48, GuiStyle.ACCENT);
        }
    }

    private void renderGrid(GuiGraphics g) {
        int x = leftPos + layout.gridX() - 1;
        int y = topPos + layout.gridY() - 1;
        GuiStyle.well(g, x - 2, y - 2, layout.columns() * BackpackLayout.SLOT + 4, layout.visibleRows() * BackpackLayout.SLOT + 4);
        for (int i = 0; i < layout.visibleSlots(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.isActive()) {
                GuiStyle.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
            }
        }
        if (layout.scrolls()) {
            float progress = (float) displayedRow() / Math.max(1, layout.maxScroll());
            GuiStyle.scrollbar(g, leftPos + layout.scrollbarX(), y - 1, layout.visibleRows() * BackpackLayout.SLOT + 2, progress, true);
        }
    }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        // Tier gem before the title.
        int color = 0xFF000000 | (menu.tier().color().getColor() == null ? 0xFFFFFF : menu.tier().color().getColor());
        int gx = leftPos + 8;
        int gy = topPos + 6;
        g.fill(gx + 1, gy, gx + 6, gy + 7, GuiStyle.OUTLINE);
        g.fill(gx, gy + 1, gx + 7, gy + 6, GuiStyle.OUTLINE);
        g.fill(gx + 1, gy + 1, gx + 6, gy + 6, color);
        g.fill(gx + 2, gy + 2, gx + 4, gy + 3, 0xFFFFFFFF);
        // Sort button.
        int sx = sortX();
        int sy = topPos + 4;
        boolean hover = mouseX >= sx && mouseX < sx + 14 && mouseY >= sy && mouseY < sy + 12;
        GuiStyle.button(g, sx, sy, 14, 12, hover, false);
        int lineColor = hover ? GuiStyle.ACCENT : GuiStyle.TEXT;
        g.fill(sx + 3, sy + 3, sx + 11, sy + 4, lineColor);
        g.fill(sx + 3, sy + 5, sx + 9, sy + 6, lineColor);
        g.fill(sx + 3, sy + 7, sx + 7, sy + 8, lineColor);
        g.fill(sx + 3, sy + 9, sx + 5, sy + 10, lineColor);
        // Used / total.
        int used = menu.usedSlots();
        int total = layout.totalSlots();
        int fill = used >= total ? GuiStyle.BAD : used * 4 >= total * 3 ? GuiStyle.WARN : GuiStyle.GOOD;
        String text = used + "/" + total;
        g.drawString(font, text, sx - 4 - font.width(text), topPos + 6, fill, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int maxWidth = sortX() - leftPos - titleLabelX - 40;
        String title = font.plainSubstrByWidth(this.title.getString(), Math.max(40, maxWidth));
        g.drawString(font, title, titleLabelX, titleLabelY, GuiStyle.TEXT, true);
        GuiStyle.labelDim(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
    }

    private int sortX() {
        return leftPos + imageWidth - 22;
    }

    private int displayedRow() {
        return pendingRow >= 0 ? pendingRow : menu.scrollRow();
    }

    // ---------------------------------------------------------------------------------------------
    // Controls in filter panels
    // ---------------------------------------------------------------------------------------------

    private static final class Control {
        final int x;
        final int y;
        final int w;
        final int h;
        final int action;
        final int value;
        final Component label;
        final int color;
        final boolean pressed;
        final Component tooltip;

        Control(int x, int y, int w, int h, int action, int value, Component label, int color, boolean pressed, Component tooltip) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
            this.value = value;
            this.label = label;
            this.color = color;
            this.pressed = pressed;
            this.tooltip = tooltip;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private List<Control> controls(Tab tab, UpgradeConfig config) {
        List<Control> list = new ArrayList<>();
        int x = tab.x() + CONTROLS_DX;
        int y = tab.y() + FILTER_DY;
        UpgradeType type = tab.type();
        if (type.hasFilter()) {
            list.add(new Control(x, y, 21, 18, BackpackMenu.ACTION_ALLOW, config.allow() ? 0 : 1,
                    Component.literal(config.allow() ? "✔" : "✖"), config.allow() ? GuiStyle.GOOD : GuiStyle.BAD, false,
                    Component.translatable(config.allow() ? "gui.omnivault.backpack.allow" : "gui.omnivault.backpack.deny")));
            list.add(new Control(x + 23, y, 21, 18, BackpackMenu.ACTION_COMPONENTS, config.matchComponents() ? 0 : 1,
                    Component.literal("{}"), config.matchComponents() ? GuiStyle.ACCENT : GuiStyle.TEXT_DIM, config.matchComponents(),
                    Component.translatable(config.matchComponents() ? "gui.omnivault.backpack.components_on" : "gui.omnivault.backpack.components_off")));
        }
        if (type.modes() > 0) {
            int next = (config.mode() + 1) % type.modes();
            String key = "gui.omnivault.backpack.mode." + type.getSerializedName() + "." + config.mode();
            list.add(new Control(x, y + 22, 44, 16, BackpackMenu.ACTION_MODE, next, Component.translatable(key), GuiStyle.TEXT, false,
                    Component.translatable(key + ".tooltip")));
        }
        if (type.hasRange()) {
            int range = BackpackUpgrades.range(config);
            list.add(new Control(x, y + 44, 12, 14, BackpackMenu.ACTION_RANGE, range - 1, Component.literal("-"), GuiStyle.TEXT, false,
                    Component.translatable("gui.omnivault.backpack.range")));
            list.add(new Control(x + 32, y + 44, 12, 14, BackpackMenu.ACTION_RANGE, range + 1, Component.literal("+"), GuiStyle.TEXT, false,
                    Component.translatable("gui.omnivault.backpack.range")));
        }
        return list;
    }

    private void send(int upgradeSlot, int action, int value, ItemStack stack) {
        Services.PLATFORM.sendToServer(new BackpackPayloads.ConfigureUpgrade(menu.containerId, upgradeSlot, action, value, stack));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ---------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int sx = sortX();
        if (button == 0 && mouseX >= sx && mouseX < sx + 14 && mouseY >= topPos + 4 && mouseY < topPos + 16) {
            Services.PLATFORM.sendToServer(new InventorySorting.SortPayload(menu.containerId, 0));
            click();
            return true;
        }
        if (button == 0 && layout.scrolls() && overScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollTo(mouseY);
            return true;
        }
        for (Tab tab : tabs()) {
            if (tab.open()) {
                int px = tab.x() + tab.width() - 19;
                int py = tab.y() + 5;
                if (mouseX >= px && mouseX < px + 13 && mouseY >= py && mouseY < py + 13) {
                    send(tab.upgradeSlot(), BackpackMenu.ACTION_ENABLED, BackpackUpgradeItem.config(tab.card()).enabled() ? 0 : 1, ItemStack.EMPTY);
                    click();
                    return true;
                }
            }
            if (tab.headerHit(mouseX, mouseY)) {
                openUpgradeSlot = tab.open() ? -1 : tab.upgradeSlot();
                click();
                return true;
            }
            if (tab.open() && tab.type() != UpgradeType.CRAFTING && tab.contains(mouseX, mouseY)) {
                if (clickFilterPanel(tab, mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickFilterPanel(Tab tab, double mouseX, double mouseY, int button) {
        UpgradeConfig config = BackpackUpgradeItem.config(tab.card());
        if (tab.type().hasFilter()) {
            int fx = tab.x() + FILTER_DX;
            int fy = tab.y() + FILTER_DY;
            if (mouseX >= fx && mouseX < fx + 3 * BackpackLayout.SLOT && mouseY >= fy && mouseY < fy + 3 * BackpackLayout.SLOT) {
                int index = (int) ((mouseX - fx) / BackpackLayout.SLOT) + 3 * (int) ((mouseY - fy) / BackpackLayout.SLOT);
                ItemStack carried = menu.getCarried();
                ItemStack ghost = button == 1 || carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
                send(tab.upgradeSlot(), BackpackMenu.ACTION_FILTER, index, ghost);
                return true;
            }
        }
        for (Control control : controls(tab, config)) {
            if (control.contains(mouseX, mouseY)) {
                send(tab.upgradeSlot(), control.action, control.value, ItemStack.EMPTY);
                click();
                return true;
            }
        }
        // Swallow clicks on the panel background so they do not drop the carried stack.
        return true;
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        int x = leftPos + layout.scrollbarX();
        int y = topPos + layout.gridY() - 2;
        return mouseX >= x && mouseX < x + 8 && mouseY >= y && mouseY < y + layout.visibleRows() * BackpackLayout.SLOT + 2;
    }

    private void scrollTo(double mouseY) {
        int top = topPos + layout.gridY() - 2;
        int height = layout.visibleRows() * BackpackLayout.SLOT + 2;
        float progress = (float) ((mouseY - top - 7.5) / (height - 15));
        requestRow(Math.round(Math.max(0, Math.min(1, progress)) * layout.maxScroll()));
    }

    private void requestRow(int row) {
        row = Math.max(0, Math.min(layout.maxScroll(), row));
        if (row != displayedRow() && minecraft != null && minecraft.gameMode != null) {
            pendingRow = row;
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BackpackMenu.SCROLL_BUTTON + row);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout.scrolls() && scrollY != 0) {
            int gridLeft = leftPos + layout.gridX() - 2;
            int gridTop = topPos + layout.gridY() - 2;
            boolean overGrid = mouseX >= gridLeft && mouseX < leftPos + layout.scrollbarX() + 9
                    && mouseY >= gridTop && mouseY < gridTop + layout.visibleRows() * BackpackLayout.SLOT + 4;
            if (overGrid) {
                requestRow(displayedRow() - (int) Math.signum(scrollY));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        if (!super.hasClickedOutside(mouseX, mouseY, left, top, button)) {
            return false;
        }
        int bayX = left - BackpackLayout.BAY_WIDTH;
        if (mouseX >= bayX && mouseX < left && mouseY >= top + 4 && mouseY < top + 4 + layout.bayHeight()) {
            return false;
        }
        for (Tab tab : tabs()) {
            if (tab.contains(mouseX, mouseY)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Tooltips
    // ---------------------------------------------------------------------------------------------

    private void renderExtraTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.hasItem()) {
            return;
        }
        int sx = sortX();
        if (mouseX >= sx && mouseX < sx + 14 && mouseY >= topPos + 4 && mouseY < topPos + 16) {
            g.renderTooltip(font, Component.translatable("gui.omnivault.backpack.sort"), mouseX, mouseY);
            return;
        }
        for (Tab tab : tabs()) {
            if (!tab.open() && tab.contains(mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>();
                lines.add(tab.card().getHoverName());
                lines.add(Component.translatable("gui.omnivault.backpack.tab_hint").withStyle(ChatFormatting.GRAY));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
            if (!tab.open()) {
                continue;
            }
            int px = tab.x() + tab.width() - 19;
            int py = tab.y() + 5;
            if (mouseX >= px && mouseX < px + 13 && mouseY >= py && mouseY < py + 13) {
                boolean enabled = BackpackUpgradeItem.config(tab.card()).enabled();
                g.renderTooltip(font, Component.translatable(enabled ? "gui.omnivault.backpack.enabled" : "gui.omnivault.backpack.disabled"), mouseX, mouseY);
                return;
            }
            if (tab.type() == UpgradeType.CRAFTING) {
                continue;
            }
            UpgradeConfig config = BackpackUpgradeItem.config(tab.card());
            for (Control control : controls(tab, config)) {
                if (control.contains(mouseX, mouseY)) {
                    g.renderTooltip(font, control.tooltip, mouseX, mouseY);
                    return;
                }
            }
            int fx = tab.x() + FILTER_DX;
            int fy = tab.y() + FILTER_DY;
            if (tab.type().hasFilter() && mouseX >= fx && mouseX < fx + 54 && mouseY >= fy && mouseY < fy + 54) {
                int index = (int) ((mouseX - fx) / BackpackLayout.SLOT) + 3 * (int) ((mouseY - fy) / BackpackLayout.SLOT);
                ItemStack ghost = config.filter().get(index);
                List<Component> lines = new ArrayList<>();
                if (!ghost.isEmpty()) {
                    lines.add(ghost.getHoverName());
                }
                lines.add(Component.translatable("gui.omnivault.backpack.filter_hint").withStyle(ChatFormatting.GRAY));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }
}
