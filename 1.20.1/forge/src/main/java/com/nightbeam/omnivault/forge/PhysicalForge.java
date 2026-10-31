package com.nightbeam.omnivault.forge;

import com.nightbeam.omnivault.content.BlockUseOverride;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * Lets vault upgrade kits, the Relocator, the OmniTool and the Quantum Link Card act on blocks that
 * would otherwise open a GUI (vanilla chests, barrels, other mods' machines, our own devices): the
 * block's own interaction is skipped so the item's {@code useOn} runs instead. Fires on both logical
 * sides, so client prediction matches the server.
 */
public final class PhysicalForge {
    private PhysicalForge() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(PhysicalForge::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (BlockUseOverride.applies(event.getItemStack(), event.getLevel(), event.getPos(), event.getLevel().getBlockState(event.getPos()), event.getEntity())) {
            event.setUseBlock(Event.Result.DENY);
        }
    }
}
