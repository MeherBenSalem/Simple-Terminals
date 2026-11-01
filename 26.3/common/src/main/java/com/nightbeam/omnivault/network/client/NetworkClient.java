package com.nightbeam.omnivault.network.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.MapCodec;
import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.client.OmniVaultClient;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.NetworkPackets;
import com.nightbeam.omnivault.network.block.CableBlock;
import com.nightbeam.omnivault.network.cell.CellItem;
import com.nightbeam.omnivault.network.menu.TerminalMenu;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class NetworkClient {
    public static final KeyMapping OPEN_WIRELESS = new KeyMapping("key.omnivault.open_wireless",
            InputConstants.KEY_G, OmniVaultClient.KEY_CATEGORY);

    private NetworkClient() {
    }

    public static void init() {
        OmniVaultClient.onClientTick(() -> {
            while (OPEN_WIRELESS.consumeClick()) {
                if (Minecraft.getInstance().player != null && Minecraft.getInstance().gui.screen() == null) {
                    Services.PLATFORM.sendToServer(new NetworkPackets.OpenWireless());
                }
            }
        });
        OmniVaultClient.registration(r -> {
            r.keyMapping(OPEN_WIRELESS);
            r.<TerminalMenu, TerminalScreen<TerminalMenu>>screen(NetworkContent.TERMINAL_MENU, TerminalScreen::new);
            r.screen(NetworkContent.CRAFTING_TERMINAL_MENU, CraftingTerminalScreen::new);
            r.screen(NetworkContent.DRIVE_MENU, DriveScreen::new);
            r.screen(NetworkContent.BUS_MENU, BusScreen::new);
            r.screen(NetworkContent.SIGNAL_EMITTER_MENU, SignalEmitterScreen::new);
            r.screen(NetworkContent.CORE_MENU, CoreScreen::new);
            r.blockEntityRenderer(NetworkContent.DRIVE_BE, DriveRenderer::new);
            r.blockEntityRenderer(NetworkContent.NEXUS_CORE_BE, CoreRenderer::new);
            r.blockEntityRenderer(NetworkContent.STORAGE_MONITOR_BE, StorageMonitorRenderer::new);
            r.standaloneModel(DriveRenderer.CELL);
            r.standaloneModel(DriveRenderer.LED);
            r.standaloneModel(CoreRenderer.CRYSTAL);
            r.standaloneModel(CoreRenderer.RING);
            // Tint index 0 on cable models is the light strip, coloured by the cable's dye. Cable items are
            // tinted with the undyed colours in their item model definitions.
            r.blockTints(List.of(new CableTint(0xFF000000 | CableBlock.CableColor.NONE.rgb)), NetworkContent.NEXUS_CABLE);
            // Inventory Cable glows amber until dyed.
            r.blockTints(List.of(new CableTint(0xFFF2B33D)), NetworkContent.INVENTORY_CABLE);
            // Tint index 0 on cell item models is the tier stripe.
            r.itemTintSource(OmniVault.id("cell_tier"), CellTierTint.MAP_CODEC);
        });
    }

    /** A cable's light strip: its dye colour, or {@code undyed} while it has none. */
    private record CableTint(int undyed) implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            CableBlock.CableColor color = state.getValue(CableBlock.COLOR);
            return color == CableBlock.CableColor.NONE ? undyed : 0xFF000000 | color.rgb;
        }

        @Override
        public Set<Property<?>> relevantProperties() {
            return Set.of(CableBlock.COLOR);
        }
    }

    /** The tier colour of a storage or fluid cell. */
    public record CellTierTint() implements ItemTintSource {
        public static final MapCodec<CellTierTint> MAP_CODEC = MapCodec.unit(new CellTierTint());

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
            return stack.getItem() instanceof CellItem cell ? 0xFF000000 | cell.tier().color : -1;
        }

        @Override
        public MapCodec<CellTierTint> type() {
            return MAP_CODEC;
        }
    }
}
