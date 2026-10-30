package com.nightbeam.omnivault.autocraft.client;

import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.client.OmniVaultClient;

public final class AutocraftClient {
    private AutocraftClient() {
    }

    public static void init() {
        OmniVaultClient.registration(r -> {
            r.screen(AutocraftContent.PATTERN_TERMINAL_MENU, PatternTerminalScreen::new);
            r.screen(AutocraftContent.PATTERN_PROVIDER_MENU, PatternProviderScreen::new);
            r.screen(AutocraftContent.ASSEMBLER_MENU, AssemblerScreen::new);
            r.screen(AutocraftContent.CRAFT_REQUEST_MENU, CraftRequestScreen::new);
            r.screen(AutocraftContent.CRAFTING_STATUS_MENU, CraftingStatusScreen::new);
            r.blockEntityRenderer(AutocraftContent.ASSEMBLER_BE, AssemblerRenderer::new);
            r.standaloneModel(AssemblerRenderer.RING);
            r.standaloneModel(AssemblerRenderer.QUANTUM_RING);
        });
    }
}
