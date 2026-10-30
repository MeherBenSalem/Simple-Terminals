package com.nightbeam.omnivault.backpack.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.backpack.BackpackBlockEntity;
import com.nightbeam.omnivault.backpack.BackpackContent;
import com.nightbeam.omnivault.backpack.BackpackItem;
import com.nightbeam.omnivault.backpack.BackpackPayloads;
import com.nightbeam.omnivault.backpack.BackpackTier;
import com.nightbeam.omnivault.client.OmniVaultClient;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.DyedItemColor;

/** Client registration for backpacks: screen, worn layer, models, dye colours and the open key. */
public final class BackpackClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.omnivault.open_backpack", InputConstants.Type.KEYSYM,
            InputConstants.KEY_B, "key.categories.inventory");

    private BackpackClient() {
    }

    /** The block model of a tier, also baked standalone for the worn layer. */
    public static ResourceLocation model(BackpackTier tier) {
        return OmniVault.id("block/" + tier.id());
    }

    public static void init() {
        BackpackItem.shiftDown = Screen::hasShiftDown;
        OmniVaultClient.registration(r -> {
            r.screen(BackpackContent.MENU, BackpackScreen::new);
            r.keyMapping(OPEN_KEY);
            for (BackpackTier tier : BackpackTier.values()) {
                r.standaloneModel(model(tier));
            }
            r.playerLayer(BackpackLayer::new);
            r.itemColor((stack, tint) -> tint == 0 ? DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR) : -1,
                    BackpackContent.itemSupplier(BackpackTier.LEATHER));
            r.blockColor((state, level, pos, tint) -> {
                if (tint != 0) {
                    return -1;
                }
                return level != null && pos != null && level.getBlockEntity(pos) instanceof BackpackBlockEntity blockEntity
                        ? blockEntity.color() : DyedItemColor.LEATHER_COLOR;
            }, BackpackContent.blockSupplier(BackpackTier.LEATHER));
        });
        OmniVaultClient.onClientTick(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_KEY.consumeClick()) {
                if (minecraft.player != null && minecraft.screen == null) {
                    Services.PLATFORM.sendToServer(BackpackPayloads.OpenBackpack.INSTANCE);
                }
            }
        });
    }
}
