package com.nightbeam.omnivault.drawer.client;

import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.drawer.AbstractDrawerBlockEntity;
import com.nightbeam.omnivault.drawer.DrawerLayout;
import com.nightbeam.omnivault.drawer.DrawerMenu;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.util.Formatting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Upgrade menu: a live miniature of the drawer front, capacity and lock status, four upgrade slots. */
public class DrawerScreen extends AbstractContainerScreen<DrawerMenu> {
    private static final int PREVIEW_X = 9;
    private static final int PREVIEW_Y = 19;
    private static final float PREVIEW_SCALE = 3.5f;
    private static final int INFO_X = 72;
    private static final int LOCK_X = 72;
    private static final int LOCK_Y = 70;

    public DrawerScreen(DrawerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 188;
        inventoryLabelY = DrawerMenu.INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        AbstractDrawerBlockEntity drawer = menu.drawer();
        if (drawer == null) {
            return;
        }
        if (overLock(mouseX, mouseY)) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable(drawer.isLocked() ? "gui.omnivault.drawer.locked" : "gui.omnivault.drawer.unlocked"),
                    Component.translatable("gui.omnivault.drawer.lock_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        DrawerLayout layout = drawer.layout();
        for (int slot = 0; slot < Math.min(layout.compartments(), drawer.compartments()); slot++) {
            int[] box = compartmentBox(layout.compartment(slot));
            if (mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3]) {
                List<Component> lines = new ArrayList<>();
                ItemKey key = drawer.keyAt(slot);
                if (key == null) {
                    lines.add(Component.translatable("gui.omnivault.drawer.empty").withStyle(ChatFormatting.GRAY));
                } else {
                    lines.add(key.view().getHoverName());
                    lines.add(Component.translatable("gui.omnivault.drawer.count", Formatting.full(drawer.countAt(slot)),
                            Formatting.full(drawer.capacityAt(slot))).withStyle(ChatFormatting.GRAY));
                }
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiStyle.window(g, x, y, imageWidth, imageHeight);
        GuiStyle.well(g, x + PREVIEW_X - 2, y + PREVIEW_Y - 2, 60, 60);
        AbstractDrawerBlockEntity drawer = menu.drawer();
        if (drawer != null) {
            renderPreview(g, drawer);
            boolean hovered = overLock(mouseX, mouseY);
            GuiStyle.button(g, x + LOCK_X, y + LOCK_Y, 20, 20, hovered, drawer.isLocked());
            TextureAtlasSprite lock = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(DrawerRenderer.LOCK_ICON);
            if (!drawer.isLocked()) {
                g.setColor(0.45f, 0.5f, 0.55f, 1f);
            }
            g.blit(x + LOCK_X + 2, y + LOCK_Y + 2, 0, 16, 16, lock);
            g.setColor(1f, 1f, 1f, 1f);
        }
        GuiStyle.panel(g, x + DrawerMenu.UPGRADE_X - 4, y + DrawerMenu.UPGRADE_Y - 4, 4 * 18 + 6, 24);
        for (int i = 0; i < AbstractDrawerBlockEntity.UPGRADE_SLOTS; i++) {
            int sx = x + DrawerMenu.UPGRADE_X - 1 + i * 18;
            int sy = y + DrawerMenu.UPGRADE_Y - 1;
            GuiStyle.slot(g, sx, sy);
            if (menu.getSlot(i).getItem().isEmpty()) {
                // A faint upward chevron marks an empty upgrade slot.
                for (int row = 0; row < 3; row++) {
                    g.fill(sx + 7 - row, sy + 7 + row, sx + 11 + row, sy + 8 + row, GuiStyle.PANEL_HIGHLIGHT);
                }
            }
        }
        GuiStyle.playerInventory(g, x + DrawerMenu.INVENTORY_X, y + DrawerMenu.INVENTORY_Y);
    }

    private void renderPreview(GuiGraphics g, AbstractDrawerBlockEntity drawer) {
        DrawerLayout layout = drawer.layout();
        for (int slot = 0; slot < Math.min(layout.compartments(), drawer.compartments()); slot++) {
            int[] box = compartmentBox(layout.compartment(slot));
            g.fill(box[0], box[1], box[2], box[3], GuiStyle.OUTLINE);
            g.fill(box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1, GuiStyle.SLOT);
            g.fill(box[0] + 1, box[1] + 1, box[2] - 1, box[1] + 2, GuiStyle.PANEL_HIGHLIGHT);
            ItemKey key = drawer.keyAt(slot);
            if (key == null) {
                continue;
            }
            int cx = (box[0] + box[2]) / 2;
            int cy = (box[1] + box[3]) / 2;
            long count = drawer.countAt(slot);
            ItemStack stack = key.toStack();
            g.renderItem(stack, cx - 8, cy - 8);
            if (count <= 0) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(cx - 8, cy - 8, cx + 8, cy + 8, 0xA01B2029);
                g.pose().popPose();
            }
            GuiStyle.smallCount(g, font, Formatting.compact(count), cx - 9, cy - 9);
        }
    }

    /** Screen rectangle (x0, y0, x1, y1) of a compartment in the front preview. */
    private int[] compartmentBox(DrawerLayout.Compartment c) {
        int ox = leftPos + PREVIEW_X;
        int oy = topPos + PREVIEW_Y;
        return new int[]{
                ox + Math.round((c.u0() - 1.5f) * PREVIEW_SCALE), oy + Math.round((c.v0() - 1.5f) * PREVIEW_SCALE),
                ox + Math.round((c.u1() - 1.5f) * PREVIEW_SCALE), oy + Math.round((c.v1() - 1.5f) * PREVIEW_SCALE)};
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        GuiStyle.title(g, font, title, 8, 7);
        GuiStyle.labelDim(g, font, playerInventoryTitle, DrawerMenu.INVENTORY_X, inventoryLabelY);
        GuiStyle.labelDim(g, font, Component.translatable("gui.omnivault.drawer.upgrades"), DrawerMenu.UPGRADE_X - 1, DrawerMenu.UPGRADE_Y - 15);
        AbstractDrawerBlockEntity drawer = menu.drawer();
        if (drawer == null) {
            return;
        }
        GuiStyle.label(g, font, Component.translatable("gui.omnivault.drawer.multiplier", drawer.capacityMultiplier()), INFO_X, 20);
        GuiStyle.labelDim(g, font, Component.translatable("gui.omnivault.drawer.per_slot", Formatting.full(drawer.capacityAt(0))), INFO_X, 31);
        g.drawString(font, Component.translatable(drawer.isLocked() ? "gui.omnivault.drawer.locked" : "gui.omnivault.drawer.unlocked"),
                INFO_X, 42, drawer.isLocked() ? GuiStyle.WARN : GuiStyle.TEXT_DIM, false);
        if (drawer.isVoiding()) {
            g.drawString(font, Component.translatable("gui.omnivault.drawer.voiding"), INFO_X, 53, GuiStyle.VIOLET, false);
        }
    }

    private boolean overLock(int mouseX, int mouseY) {
        int x = leftPos + LOCK_X;
        int y = topPos + LOCK_Y;
        return mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overLock((int) mouseX, (int) mouseY) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, DrawerMenu.BUTTON_TOGGLE_LOCK);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
