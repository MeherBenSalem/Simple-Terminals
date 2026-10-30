package com.nightbeam.omnivault.neoforge.drawer;

import com.nightbeam.omnivault.drawer.DrawerInteraction;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Creative players break blocks instantly on the first left click, before {@code Block#attack} runs.
 * This cancels that click on a drawer's front (on both sides) and extracts instead; survival clicks
 * reach {@code DrawerBlock#attack} as usual.
 */
public final class DrawerNeoForgeEvents {
    private DrawerNeoForgeEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DrawerNeoForgeEvents::onLeftClickBlock);
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && DrawerInteraction.onLeftClickBlock(event.getEntity(), event.getLevel(), event.getPos(), event.getFace())) {
            event.setCanceled(true);
        }
    }
}
