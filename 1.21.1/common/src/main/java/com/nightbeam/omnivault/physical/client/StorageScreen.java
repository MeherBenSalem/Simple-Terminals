package com.nightbeam.omnivault.physical.client;

import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.physical.StorageLayout;
import com.nightbeam.omnivault.physical.StorageMenu;
import com.nightbeam.omnivault.physical.linked.LinkedFrequency;
import com.nightbeam.omnivault.physical.vault.VaultTier;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.util.InventorySorting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/** Vault, Linked Vault and Linked Pouch screen: a wide slot grid in the shared OmniVault style. */
public class StorageScreen extends AbstractContainerScreen<StorageMenu> {
    private static final int BUTTON = 12;
    private static final int CHIP = 8;

    private final StorageLayout layout;

    public StorageScreen(StorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.layout = menu.layout();
        this.imageWidth = layout.width();
        this.imageHeight = layout.height();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = layout.inventoryX();
        this.inventoryLabelY = layout.inventoryY() - 11;
    }

    private int sortX() {
        return leftPos + imageWidth - 8 - BUTTON;
    }

    private int sortY() {
        return topPos + 4;
    }

    private boolean overSort(double mouseX, double mouseY) {
        return mouseX >= sortX() && mouseX < sortX() + BUTTON && mouseY >= sortY() && mouseY < sortY() + BUTTON;
    }

    private int chipsX() {
        return sortX() - 6 - 3 * (CHIP + 2);
    }

    private boolean overChips(double mouseX, double mouseY) {
        int x = chipsX() - (menu.frequency() != null && menu.frequency().isPrivate() ? 10 : 0);
        return menu.frequency() != null && mouseX >= x && mouseX < sortX() - 4 && mouseY >= topPos + 4 && mouseY < topPos + 16;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (overSort(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.omnivault.vault.sort"), mouseX, mouseY);
        } else if (overChips(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            menu.frequency().appendTooltip(lines);
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiStyle.window(g, x, y, imageWidth, imageHeight);

        int gridWidth = layout.columns() * 18;
        int gridHeight = layout.rows() * 18;
        GuiStyle.well(g, x + layout.gridX() - 2, y + layout.gridY() - 2, gridWidth + 2, gridHeight + 2);
        GuiStyle.slots(g, x + layout.gridX() - 1, y + layout.gridY() - 1, layout.columns(), layout.rows());
        GuiStyle.playerInventory(g, x + layout.inventoryX(), y + layout.inventoryY());

        // Tier (or channel) accent under the title.
        int accent = accentColor();
        int titleWidth = Math.min(font.width(title), counterRight() - 40 - 8);
        g.fill(x + 8, y + 15, x + 8 + titleWidth, y + 16, accent);
        g.fill(x + 8 + titleWidth, y + 15, x + 8 + titleWidth + 6, y + 16, GuiStyle.darken(accent));

        boolean hovered = overSort(mouseX, mouseY);
        GuiStyle.button(g, sortX(), sortY(), BUTTON, BUTTON, hovered, false);
        int icon = hovered ? GuiStyle.ACCENT : GuiStyle.TEXT;
        g.fill(sortX() + 3, sortY() + 3, sortX() + 9, sortY() + 4, icon);
        g.fill(sortX() + 3, sortY() + 5, sortX() + 8, sortY() + 6, icon);
        g.fill(sortX() + 3, sortY() + 7, sortX() + 7, sortY() + 8, icon);
        g.fill(sortX() + 3, sortY() + 9, sortX() + 5, sortY() + 10, icon);

        LinkedFrequency frequency = menu.frequency();
        if (frequency != null) {
            int cx = chipsX();
            for (int i = 0; i < 3; i++) {
                int chipX = cx + i * (CHIP + 2);
                g.fill(chipX - 1, y + 5, chipX + CHIP + 1, y + 6 + CHIP, GuiStyle.OUTLINE);
                g.fill(chipX, y + 6, chipX + CHIP, y + 5 + CHIP, LinkedFrequency.tint(frequency.color(i)));
                g.fill(chipX, y + 6, chipX + CHIP, y + 7, 0x50FFFFFF);
                g.fill(chipX, y + 4 + CHIP, chipX + CHIP, y + 5 + CHIP, 0x50000000);
            }
            if (frequency.isPrivate()) {
                drawPadlock(g, cx - 10, y + 5);
            }
        }
    }

    private int accentColor() {
        VaultTier tier = menu.tier();
        if (tier != null) {
            return tier.uiColor();
        }
        LinkedFrequency frequency = menu.frequency();
        return frequency != null && frequency.isPrivate() ? GuiStyle.VIOLET : GuiStyle.ACCENT;
    }

    private static void drawPadlock(GuiGraphics g, int x, int y) {
        int body = GuiStyle.VIOLET;
        g.fill(x + 2, y, x + 5, y + 1, body);
        g.fill(x + 1, y + 1, x + 2, y + 4, body);
        g.fill(x + 5, y + 1, x + 6, y + 4, body);
        g.fill(x, y + 4, x + 7, y + 9, body);
        g.fill(x + 3, y + 5, x + 4, y + 7, GuiStyle.OUTLINE);
    }

    /** Right edge (screen-relative) of the "used/total" counter in the title bar. */
    private int counterRight() {
        int right = imageWidth - 8 - BUTTON - 6;
        LinkedFrequency frequency = menu.frequency();
        if (frequency != null) {
            right = chipsX() - leftPos - 6 - (frequency.isPrivate() ? 10 : 0);
        }
        return right;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int used = 0;
        for (int i = 0; i < layout.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.hasItem()) {
                used++;
            }
        }
        String count = used + "/" + layout.size();
        int countLeft = counterRight() - font.width(count);
        g.drawString(font, count, countLeft, titleLabelY, used >= layout.size() ? GuiStyle.WARN : GuiStyle.TEXT_DIM, false);

        int maxWidth = countLeft - 6 - titleLabelX;
        Component shown = title;
        if (font.width(title) > maxWidth) {
            shown = Component.literal(font.plainSubstrByWidth(title.getString(), Math.max(0, maxWidth - font.width("…"))) + "…");
        }
        GuiStyle.title(g, font, shown, titleLabelX, titleLabelY);
        if (!layout.compact()) {
            GuiStyle.labelDim(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overSort(mouseX, mouseY)) {
            Services.PLATFORM.sendToServer(new InventorySorting.SortPayload(menu.containerId, 0));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
