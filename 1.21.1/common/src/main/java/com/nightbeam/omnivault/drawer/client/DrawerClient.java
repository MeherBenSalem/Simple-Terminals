package com.nightbeam.omnivault.drawer.client;

import com.nightbeam.omnivault.client.OmniVaultClient;
import com.nightbeam.omnivault.drawer.AbstractDrawerBlockEntity;
import com.nightbeam.omnivault.drawer.DrawerContent;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Client registration for drawers: the front renderer and the upgrade screen. */
public final class DrawerClient {
    private DrawerClient() {
    }

    public static void init() {
        OmniVaultClient.registration(r -> {
            BlockEntityRendererProvider<AbstractDrawerBlockEntity> renderer = DrawerRenderer::new;
            r.blockEntityRenderer(DrawerContent.DRAWER_BE, renderer);
            r.blockEntityRenderer(DrawerContent.COMPACTING_BE, renderer);
            r.screen(DrawerContent.DRAWER_MENU, DrawerScreen::new);
        });
    }
}
