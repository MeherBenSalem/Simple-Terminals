package com.nightbeam.omnivault.neoforge;

import com.nightbeam.omnivault.physical.BlockUseOverride;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Lets vault upgrade kits and the Relocator act on blocks that would otherwise open a GUI (vanilla
 * chests, barrels, other mods' machines): the block's own interaction is skipped so the item's
 * {@code useOn} runs instead. Fires on both logical sides, so client prediction matches the server.
 */
public final class PhysicalNeoForge {
    private PhysicalNeoForge() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(PhysicalNeoForge::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (BlockUseOverride.applies(event.getItemStack(), event.getLevel(), event.getPos(), event.getLevel().getBlockState(event.getPos()), event.getEntity())) {
            event.setUseBlock(TriState.FALSE);
        }
    }
}
