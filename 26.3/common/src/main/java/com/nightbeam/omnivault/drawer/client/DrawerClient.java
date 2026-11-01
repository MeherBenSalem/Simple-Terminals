package com.nightbeam.omnivault.drawer.client;

import com.nightbeam.omnivault.client.OmniVaultClient;
import com.nightbeam.omnivault.drawer.DrawerContent;

/** Client registration for drawers: the front renderer and the upgrade screen. */
public final class DrawerClient {
    private DrawerClient() {
    }

    public static void init() {
        OmniVaultClient.registration(r -> {
            r.blockEntityRenderer(DrawerContent.DRAWER_BE, DrawerRenderer::new);
            r.blockEntityRenderer(DrawerContent.COMPACTING_BE, DrawerRenderer::new);
            r.screen(DrawerContent.DRAWER_MENU, DrawerScreen::new);
        });
    }
}
