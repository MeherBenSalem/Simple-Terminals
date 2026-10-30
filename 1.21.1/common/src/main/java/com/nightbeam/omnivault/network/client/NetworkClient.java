package com.nightbeam.omnivault.network.client;

import com.nightbeam.omnivault.client.OmniVaultClient;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.block.CableBlock;
import com.nightbeam.omnivault.network.cell.CellTier;
import com.nightbeam.omnivault.network.menu.TerminalMenu;

public final class NetworkClient {
    private NetworkClient() {
    }

    public static void init() {
        OmniVaultClient.registration(r -> {
            r.<TerminalMenu, TerminalScreen<TerminalMenu>>screen(NetworkContent.TERMINAL_MENU, TerminalScreen::new);
            r.screen(NetworkContent.CRAFTING_TERMINAL_MENU, CraftingTerminalScreen::new);
            r.screen(NetworkContent.DRIVE_MENU, DriveScreen::new);
            r.screen(NetworkContent.BUS_MENU, BusScreen::new);
            r.screen(NetworkContent.SIGNAL_EMITTER_MENU, SignalEmitterScreen::new);
            r.screen(NetworkContent.CORE_MENU, CoreScreen::new);
            r.blockEntityRenderer(NetworkContent.DRIVE_BE, DriveRenderer::new);
            r.blockEntityRenderer(NetworkContent.NEXUS_CORE_BE, CoreRenderer::new);
            r.standaloneModel(DriveRenderer.CELL);
            r.standaloneModel(DriveRenderer.LED);
            r.standaloneModel(CoreRenderer.CRYSTAL);
            r.standaloneModel(CoreRenderer.RING);
            // Tint index 0 on cable models is the light strip, coloured by the cable's dye.
            r.blockColor((state, level, pos, tintIndex) -> tintIndex == 0 ? 0xFF000000 | state.getValue(CableBlock.COLOR).rgb : -1,
                    NetworkContent.NEXUS_CABLE);
            r.itemColor((stack, tintIndex) -> tintIndex == 0 ? 0xFF000000 | CableBlock.CableColor.NONE.rgb : -1, NetworkContent.NEXUS_CABLE);
            // Tint index 0 on cell item models is the tier stripe.
            for (CellTier tier : CellTier.values()) {
                r.itemColor((stack, tintIndex) -> tintIndex == 0 ? tier.color : -1, NetworkContent.CELLS.get(tier));
            }
        });
    }
}
