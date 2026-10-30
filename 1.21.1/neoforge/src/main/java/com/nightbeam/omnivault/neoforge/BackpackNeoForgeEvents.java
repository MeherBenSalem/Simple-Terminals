package com.nightbeam.omnivault.neoforge;

import com.nightbeam.omnivault.backpack.BackpackHooks;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/** Forwards the NeoForge events backpack upgrades need to {@link BackpackHooks}. */
public final class BackpackNeoForgeEvents {
    private BackpackNeoForgeEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BackpackNeoForgeEvents::onPickup);
        NeoForge.EVENT_BUS.addListener(BackpackNeoForgeEvents::onJoin);
    }

    private static void onPickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity entity = event.getItemEntity();
        if (event.canPickup().isFalse()) {
            return;
        }
        boolean allowed = event.canPickup().isTrue()
                || (entity.getTarget() == null || entity.getTarget().equals(event.getPlayer().getUUID()));
        if (allowed && BackpackHooks.onItemPickup(event.getPlayer(), entity)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity entity) {
            BackpackHooks.onItemEntityJoin(entity);
        }
    }
}
