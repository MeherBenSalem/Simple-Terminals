package com.nightbeam.omnivault.network.client;

import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.client.gui.OVButton;
import com.nightbeam.omnivault.network.NetworkPackets;
import com.nightbeam.omnivault.network.menu.TerminalMenu;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.util.Formatting;
import com.nightbeam.omnivault.util.InventorySorting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The network grid: search ("@mod", "#tag" and plain words, all combined), sort by name, count,
 * mod or registry order, scroll, and click to take or insert items.
 */
public class TerminalScreen<T extends TerminalMenu> extends AbstractContainerScreen<T> {
    private static final int SCROLL_X = 174;
    /** The search well, right-aligned with the scrollbar; the title gets the space to its left. */
    private static final int SEARCH_X = 104;
    private static final int SEARCH_WIDTH = SCROLL_X + 8 - SEARCH_X;

    // Remembered for the session across every terminal.
    private static SortMode sortMode = SortMode.COUNT;
    private static boolean descending = true;
    private static ViewMode viewMode = ViewMode.ALL;
    private static String lastSearch = "";
    private static boolean fluidMode;

    private EditBox search;
    private final List<TerminalMenu.ClientEntry> view = new ArrayList<>();
    private final List<Map.Entry<FluidKey, Long>> fluidView = new ArrayList<>();
    private int seenVersion = -1;
    private String seenSearch = null;
    private int scrollRow;
    private boolean draggingScrollbar;

    public TerminalScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 194;
        imageHeight = menu.playerInventoryY() + 82;
        inventoryLabelY = menu.playerInventoryY() - 11;
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + SEARCH_X + 3, topPos + 6, SEARCH_WIDTH - 6, 10, Component.translatable("gui.omnivault.terminal.search"));
        search.setBordered(false);
        search.setTooltip(Tooltip.create(Component.translatable("gui.omnivault.terminal.search_help")));
        search.setTooltipDelay(Duration.ofMillis(600));
        search.setMaxLength(80);
        search.setTextColor(GuiStyle.TEXT);
        search.setValue(lastSearch);
        search.setResponder(text -> {
            lastSearch = text;
            scrollRow = 0;
        });
        addRenderableWidget(search);
        int bx = leftPos - 20;
        int by = topPos + 20;
        addRenderableWidget(new OVButton(bx, by, 18, 18, () -> Component.literal(sortMode.glyph),
                () -> Component.translatable("gui.omnivault.terminal.sort", Component.translatable("gui.omnivault.terminal.sort." + sortMode.name().toLowerCase(Locale.ROOT))),
                () -> {
                    sortMode = SortMode.values()[(sortMode.ordinal() + 1) % SortMode.values().length];
                    seenVersion = -1;
                }));
        addRenderableWidget(new OVButton(bx, by + 20, 18, 18, () -> Component.literal(descending ? "▼" : "▲"),
                () -> Component.translatable(descending ? "gui.omnivault.terminal.descending" : "gui.omnivault.terminal.ascending"),
                () -> {
                    descending = !descending;
                    seenVersion = -1;
                }));
        addRenderableWidget(new OVButton(bx, by + 40, 18, 18, () -> Component.literal(viewMode.glyph),
                () -> Component.translatable("gui.omnivault.terminal.view." + viewMode.name().toLowerCase(Locale.ROOT)),
                () -> {
                    viewMode = ViewMode.values()[(viewMode.ordinal() + 1) % ViewMode.values().length];
                    seenVersion = -1;
                    scrollRow = 0;
                }));
        addRenderableWidget(new OVButton(bx, by + 64, 18, 18, () -> Component.literal(fluidMode ? "\u2248" : "\u25A3"),
                () -> Component.translatable(fluidMode ? "gui.omnivault.terminal.mode.fluids" : "gui.omnivault.terminal.mode.items"),
                () -> {
                    fluidMode = !fluidMode;
                    seenVersion = -1;
                    scrollRow = 0;
                }));
    }

    // -- view ------------------------------------------------------------------------------------

    private void refreshView() {
        if (seenVersion == menu.clientVersion() && search.getValue().equals(seenSearch)) {
            return;
        }
        seenVersion = menu.clientVersion();
        seenSearch = search.getValue();
        view.clear();
        String[] terms = seenSearch.toLowerCase(Locale.ROOT).trim().split("\\s+");
        fluidView.clear();
        for (Map.Entry<FluidKey, Long> fluid : menu.clientFluids().entrySet()) {
            String name = fluidName(fluid.getKey()).getString().toLowerCase(Locale.ROOT);
            boolean ok = true;
            for (String term : terms) {
                if (!term.isEmpty() && !name.contains(term) && !fluid.getKey().id().toString().contains(term)) {
                    ok = false;
                }
            }
            if (ok) {
                fluidView.add(Map.entry(fluid.getKey(), fluid.getValue()));
            }
        }
        Comparator<Map.Entry<FluidKey, Long>> fluidOrder = sortMode == SortMode.COUNT
                ? Map.Entry.comparingByValue()
                : Comparator.comparing(e -> fluidName(e.getKey()).getString());
        fluidView.sort(descending ? fluidOrder.reversed() : fluidOrder);
        for (TerminalMenu.ClientEntry entry : menu.clientEntries().values()) {
            if (viewMode == ViewMode.STORED && entry.count() <= 0 || viewMode == ViewMode.CRAFTABLE && !entry.craftable()) {
                continue;
            }
            if (matches(entry.key(), terms)) {
                view.add(entry);
            }
        }
        Comparator<TerminalMenu.ClientEntry> order = switch (sortMode) {
            case NAME -> Comparator.comparing(e -> e.key().view().getHoverName().getString().toLowerCase(Locale.ROOT));
            case COUNT -> Comparator.<TerminalMenu.ClientEntry>comparingLong(TerminalMenu.ClientEntry::count)
                    .thenComparing(e -> e.key().view().getHoverName().getString());
            case MOD -> Comparator.<TerminalMenu.ClientEntry, String>comparing(e -> BuiltInRegistries.ITEM.getKey(e.key().item()).getNamespace())
                    .thenComparing(e -> e.key().view().getHoverName().getString());
            case ID -> Comparator.comparing(TerminalMenu.ClientEntry::key, InventorySorting.ORDER);
        };
        view.sort(descending ? order.reversed() : order);
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
    }

    private static boolean matches(ItemKey key, String[] terms) {
        ItemStack stack = key.view();
        for (String term : terms) {
            if (term.isEmpty()) {
                continue;
            }
            boolean ok;
            if (term.startsWith("@")) {
                String mod = term.substring(1);
                String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
                ok = namespace.contains(mod) || Services.PLATFORM.isModLoaded(namespace) && modName(namespace).contains(mod);
            } else if (term.startsWith("#")) {
                String tag = term.substring(1);
                ok = stack.getTags().anyMatch(t -> t.location().toString().contains(tag));
            } else {
                ok = stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(term)
                        || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains(term);
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public static Component fluidName(FluidKey key) {
        var block = key.fluid().defaultFluidState().createLegacyBlock();
        return block.isAir() ? key.icon().getHoverName() : block.getBlock().getName();
    }

    private static String bucketText(long millibuckets) {
        return millibuckets < FluidKey.BUCKET ? millibuckets + "mB" : Formatting.compact(millibuckets / FluidKey.BUCKET) + "B";
    }

    private int viewSize() {
        return fluidMode ? fluidView.size() : view.size();
    }

    private static String modName(String namespace) {
        return namespace.replace('_', ' ');
    }

    private int totalRows() {
        return (viewSize() + TerminalMenu.COLUMNS - 1) / TerminalMenu.COLUMNS;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - menu.rows());
    }

    private TerminalMenu.ClientEntry entryAt(double mouseX, double mouseY) {
        int column = (int) Math.floor((mouseX - leftPos - TerminalMenu.GRID_X) / 18);
        int row = (int) Math.floor((mouseY - topPos - TerminalMenu.GRID_Y) / 18);
        if (column < 0 || column >= TerminalMenu.COLUMNS || row < 0 || row >= menu.rows()) {
            return null;
        }
        int index = (scrollRow + row) * TerminalMenu.COLUMNS + column;
        return !fluidMode && index < view.size() ? view.get(index) : null;
    }

    private Map.Entry<FluidKey, Long> fluidAt(double mouseX, double mouseY) {
        int column = (int) Math.floor((mouseX - leftPos - TerminalMenu.GRID_X) / 18);
        int row = (int) Math.floor((mouseY - topPos - TerminalMenu.GRID_Y) / 18);
        if (!fluidMode || column < 0 || column >= TerminalMenu.COLUMNS || row < 0 || row >= menu.rows()) {
            return null;
        }
        int index = (scrollRow + row) * TerminalMenu.COLUMNS + column;
        return index < fluidView.size() ? fluidView.get(index) : null;
    }

    private boolean overGrid(double mouseX, double mouseY) {
        return mouseX >= leftPos + TerminalMenu.GRID_X && mouseX < leftPos + TerminalMenu.GRID_X + TerminalMenu.COLUMNS * 18
                && mouseY >= topPos + TerminalMenu.GRID_Y && mouseY < topPos + menu.gridBottom();
    }

    // -- rendering ---------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshView();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderGrid(graphics, mouseX, mouseY);
        if (search.getValue().isEmpty() && !search.isFocused()) {
            graphics.drawString(font, Component.translatable("gui.omnivault.terminal.search_hint"), leftPos + SEARCH_X + 3, topPos + 6, GuiStyle.TEXT_DIM, false);
        }
        TerminalMenu.ClientEntry hovered = entryAt(mouseX, mouseY);
        Map.Entry<FluidKey, Long> hoveredFluid = fluidAt(mouseX, mouseY);
        if (hoveredFluid != null) {
            graphics.renderComponentTooltip(font, List.of(fluidName(hoveredFluid.getKey()),
                    Component.translatable("gui.omnivault.terminal.fluid_amount", Formatting.full(hoveredFluid.getValue())).withStyle(ChatFormatting.AQUA),
                    Component.translatable("gui.omnivault.terminal.fluid_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (hovered != null && menu.getCarried().isEmpty()) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hovered.key().view()));
            lines.add(Component.translatable("gui.omnivault.terminal.stored", Formatting.full(hovered.count())).withStyle(ChatFormatting.AQUA));
            if (hovered.craftable()) {
                lines.add(Component.translatable("gui.omnivault.terminal.craftable_hint").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            graphics.renderTooltip(font, lines, hovered.key().view().getTooltipImage(), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiStyle.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        GuiStyle.well(graphics, leftPos + SEARCH_X, topPos + 4, SEARCH_WIDTH, 13);
        GuiStyle.well(graphics, leftPos + TerminalMenu.GRID_X - 1, topPos + TerminalMenu.GRID_Y - 1, TerminalMenu.COLUMNS * 18 + 2, menu.rows() * 18 + 2);
        GuiStyle.slots(graphics, leftPos + TerminalMenu.GRID_X - 1 + 1, topPos + TerminalMenu.GRID_Y, TerminalMenu.COLUMNS, menu.rows());
        GuiStyle.scrollbar(graphics, leftPos + SCROLL_X, topPos + TerminalMenu.GRID_Y - 1, menu.rows() * 18 + 2,
                maxScroll() == 0 ? 0 : scrollRow / (float) maxScroll(), maxScroll() > 0);
        GuiStyle.playerInventory(graphics, leftPos + 8, topPos + menu.playerInventoryY());
        renderExtraBackground(graphics, mouseX, mouseY);
    }

    /** Subclasses draw their own panels (crafting grid, pattern slots). */
    protected void renderExtraBackground(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int start = scrollRow * TerminalMenu.COLUMNS;
        for (int i = 0; fluidMode && i < menu.rows() * TerminalMenu.COLUMNS; i++) {
            int index = start + i;
            if (index >= fluidView.size()) {
                break;
            }
            Map.Entry<FluidKey, Long> fluid = fluidView.get(index);
            int x = leftPos + TerminalMenu.GRID_X + (i % TerminalMenu.COLUMNS) * 18;
            int y = topPos + TerminalMenu.GRID_Y + (i / TerminalMenu.COLUMNS) * 18;
            graphics.renderItem(fluid.getKey().icon(), x, y);
            GuiStyle.smallCount(graphics, font, bucketText(fluid.getValue()), x - 1, y - 1);
        }
        for (int i = 0; !fluidMode && i < menu.rows() * TerminalMenu.COLUMNS; i++) {
            int index = start + i;
            if (index >= view.size()) {
                break;
            }
            TerminalMenu.ClientEntry entry = view.get(index);
            int x = leftPos + TerminalMenu.GRID_X + (i % TerminalMenu.COLUMNS) * 18;
            int y = topPos + TerminalMenu.GRID_Y + (i / TerminalMenu.COLUMNS) * 18;
            graphics.renderItem(entry.key().view(), x, y);
            graphics.renderItemDecorations(font, entry.key().view(), x, y, "");
            if (entry.count() > 0) {
                GuiStyle.smallCount(graphics, font, Formatting.compact(entry.count()), x - 1, y - 1);
            } else if (entry.craftable()) {
                GuiStyle.smallCount(graphics, font, "Craft", x - 1, y - 1);
            }
        }
        if (overGrid(mouseX, mouseY)) {
            int column = (mouseX - leftPos - TerminalMenu.GRID_X) / 18;
            int row = (mouseY - topPos - TerminalMenu.GRID_Y) / 18;
            int x = leftPos + TerminalMenu.GRID_X + column * 18;
            int y = topPos + TerminalMenu.GRID_Y + row * 18;
            graphics.fillGradient(x, y, x + 16, y + 16, 300, 0x8038E1FF, 0x8038E1FF);
        }
        if (!menu.clientOnline()) {
            int x0 = leftPos + TerminalMenu.GRID_X;
            int y0 = topPos + TerminalMenu.GRID_Y;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 350);
            graphics.fill(x0, y0, x0 + TerminalMenu.COLUMNS * 18, y0 + menu.rows() * 18, 0xC0101318);
            GuiStyle.centered(graphics, font, Component.translatable("gui.omnivault.terminal.offline"), x0 + TerminalMenu.COLUMNS * 9,
                    y0 + menu.rows() * 9 - 4, GuiStyle.BAD);
            graphics.pose().popPose();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiStyle.title(graphics, font, GuiStyle.fit(font, title, SEARCH_X - 4 - 8), 8, 6);
        GuiStyle.labelDim(graphics, font, playerInventoryTitle, 8, inventoryLabelY);
        Component summary = Component.translatable(fluidMode ? "gui.omnivault.terminal.fluid_summary" : "gui.omnivault.terminal.summary",
                Formatting.compact(viewSize()));
        graphics.drawString(font, summary, imageWidth - 8 - font.width(summary), inventoryLabelY, GuiStyle.TEXT_DIM, false);
    }

    // -- input -------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= leftPos + SCROLL_X && mouseX < leftPos + SCROLL_X + 8
                && mouseY >= topPos + TerminalMenu.GRID_Y && mouseY < topPos + menu.gridBottom()) {
            draggingScrollbar = true;
            scrollTo(mouseY);
            return true;
        }
        if (overGrid(mouseX, mouseY) && menu.clientOnline() && fluidMode) {
            ItemStack carried = menu.getCarried();
            Map.Entry<FluidKey, Long> fluid = fluidAt(mouseX, mouseY);
            if (FluidKey.fromBucket(carried.getItem()) != null) {
                Services.PLATFORM.sendToServer(new NetworkPackets.FluidClick(menu.containerId, Optional.empty()));
            } else if (carried.is(Items.BUCKET) && fluid != null) {
                Services.PLATFORM.sendToServer(new NetworkPackets.FluidClick(menu.containerId, Optional.of(fluid.getKey())));
            }
            return true;
        }
        if (overGrid(mouseX, mouseY) && menu.clientOnline()) {
            if (!menu.getCarried().isEmpty()) {
                if (button == 0 || button == 1) {
                    Services.PLATFORM.sendToServer(new NetworkPackets.GridInsert(menu.containerId, button == 1));
                }
                return true;
            }
            TerminalMenu.ClientEntry entry = entryAt(mouseX, mouseY);
            if (entry != null) {
                int action;
                if (entry.craftable() && (entry.count() <= 0 || button == 2 || hasControlDown())) {
                    action = TerminalMenu.ACTION_CRAFT;
                } else if (button == 0 && hasShiftDown()) {
                    action = TerminalMenu.ACTION_QUICK_MOVE;
                } else if (button == 1) {
                    action = hasShiftDown() ? TerminalMenu.ACTION_TAKE_ONE : TerminalMenu.ACTION_TAKE_HALF;
                } else if (button == 0) {
                    action = TerminalMenu.ACTION_TAKE_STACK;
                } else {
                    return true;
                }
                Services.PLATFORM.sendToServer(new NetworkPackets.GridClick(menu.containerId, entry.key(), action));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    private void scrollTo(double mouseY) {
        float progress = (float) ((mouseY - topPos - TerminalMenu.GRID_Y - 7) / (menu.rows() * 18 - 15));
        scrollRow = Mth.clamp(Math.round(progress * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && (overGrid(mouseX, mouseY) || mouseX >= leftPos + SCROLL_X && mouseX < leftPos + SCROLL_X + 8)) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                search.setFocused(false);
                return true;
            }
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (search.isFocused()) {
            return search.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private enum SortMode {
        NAME("Az"), COUNT("#"), MOD("@"), ID("Id");

        final String glyph;

        SortMode(String glyph) {
            this.glyph = glyph;
        }
    }

    private enum ViewMode {
        ALL("*"), STORED("S"), CRAFTABLE("C");

        final String glyph;

        ViewMode(String glyph) {
            this.glyph = glyph;
        }
    }
}
