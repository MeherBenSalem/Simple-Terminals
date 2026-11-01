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
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Client registration for backpacks: screen, worn layer, models, dye colours and the open key. */
public final class BackpackClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.omnivault.open_backpack", InputConstants.Type.KEYSYM,
            InputConstants.KEY_B, KeyMapping.Category.INVENTORY);

    private BackpackClient() {
    }

    /** The block model of a tier, also baked standalone for the worn layer. */
    public static Identifier model(BackpackTier tier) {
        return OmniVault.id("block/" + tier.id());
    }

    public static void init() {
        BackpackItem.shiftDown = () -> Minecraft.getInstance().hasShiftDown();
        OmniVaultClient.registration(r -> {
            r.screen(BackpackContent.MENU, BackpackScreen::new);
            r.keyMapping(OPEN_KEY);
            for (BackpackTier tier : BackpackTier.values()) {
                r.standaloneModel(model(tier));
            }
            r.playerLayer(BackpackLayer::new);
            // The leather backpack item is tinted by a minecraft:dye tint in its item model definition.
            r.blockTints(List.of(new LeatherTint()), BackpackContent.blockSupplier(BackpackTier.LEATHER));
        });
        OmniVaultClient.onClientTick(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_KEY.consumeClick()) {
                if (minecraft.player != null && minecraft.gui.screen() == null) {
                    Services.PLATFORM.sendToServer(BackpackPayloads.OpenBackpack.INSTANCE);
                }
            }
        });
    }

    /** A placed leather backpack takes the dye of the backpack it was placed from. */
    private record LeatherTint() implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            return DyedItemColor.LEATHER_COLOR;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return level.getBlockEntity(pos) instanceof BackpackBlockEntity blockEntity ? blockEntity.color() : DyedItemColor.LEATHER_COLOR;
        }
    }
}
