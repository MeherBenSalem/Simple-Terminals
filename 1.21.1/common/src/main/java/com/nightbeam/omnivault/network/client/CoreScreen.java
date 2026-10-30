package com.nightbeam.omnivault.network.client;

import com.nightbeam.omnivault.client.gui.GuiStyle;
import com.nightbeam.omnivault.network.NetworkPackets;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.menu.CoreMenu;
import com.nightbeam.omnivault.util.Formatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Network overview opened from the Nexus Core. */
public class CoreScreen extends AbstractContainerScreen<CoreMenu> {
    public CoreScreen(CoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 220;
        imageHeight = 176;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiStyle.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        GuiStyle.panel(graphics, leftPos + 8, topPos + 20, imageWidth - 16, 58);
        GuiStyle.well(graphics, leftPos + 8, topPos + 84, imageWidth - 16, imageHeight - 92);
        NetworkPackets.CoreStats stats = menu.stats();
        if (stats == null) {
            return;
        }
        float storage = stats.capacity() > 0 ? stats.stored() / (float) stats.capacity() : 0;
        GuiStyle.bar(graphics, leftPos + 14, topPos + 46, imageWidth - 28, 7, storage, storage > 0.9f ? GuiStyle.BAD : storage > 0.75f ? GuiStyle.WARN : GuiStyle.ACCENT);
        float energy = stats.energyMax() > 0 ? stats.energy() / (float) stats.energyMax() : 0;
        GuiStyle.bar(graphics, leftPos + 14, topPos + 66, imageWidth - 28, 7, energy, GuiStyle.WARN);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiStyle.title(graphics, font, title, 8, 6);
        NetworkPackets.CoreStats stats = menu.stats();
        if (stats == null) {
            GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.core.loading"), 14, 26);
            return;
        }
        StorageNetwork.State state = StorageNetwork.State.values()[Math.min(stats.state(), StorageNetwork.State.values().length - 1)];
        int stateColor = state == StorageNetwork.State.ONLINE ? GuiStyle.GOOD : GuiStyle.BAD;
        Component stateText = Component.translatable("gui.omnivault.core.state." + state.name().toLowerCase(java.util.Locale.ROOT));
        graphics.drawString(font, stateText, imageWidth - 8 - font.width(stateText), 6, stateColor, false);
        GuiStyle.label(graphics, font, Component.translatable("gui.omnivault.core.storage",
                Formatting.full(stats.stored()), stats.capacity() > 0 ? Formatting.full(stats.capacity()) : "?", Formatting.full(stats.types())), 14, 26);
        GuiStyle.labelDim(graphics, font, Component.translatable("gui.omnivault.core.devices", stats.nodes()), 14, 36);
        GuiStyle.label(graphics, font, Component.translatable("gui.omnivault.core.energy",
                Formatting.compact(stats.energy()), Formatting.compact(stats.energyMax()), stats.usage()), 14, 56);
        List<Map.Entry<String, Integer>> devices = new ArrayList<>(stats.devices().entrySet());
        devices.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        int y = 89;
        int column = 0;
        for (Map.Entry<String, Integer> entry : devices) {
            int x = 14 + column * ((imageWidth - 28) / 2);
            Component name = Component.translatable(entry.getKey());
            graphics.drawString(font, Component.literal(entry.getValue() + "× ").append(name), x, y, GuiStyle.TEXT, false);
            column++;
            if (column == 2) {
                column = 0;
                y += 11;
                if (y > imageHeight - 18) {
                    break;
                }
            }
        }
    }
}
