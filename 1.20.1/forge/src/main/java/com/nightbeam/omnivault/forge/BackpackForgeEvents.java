package com.nightbeam.omnivault.forge;

import com.nightbeam.omnivault.backpack.BackpackHooks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

/** Forwards the Forge events backpack upgrades need to {@link BackpackHooks}. */
public final class BackpackForgeEvents {
    private BackpackForgeEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, BackpackForgeEvents::onPickup);
        MinecraftForge.EVENT_BUS.addListener(BackpackForgeEvents::onJoin);
    }

    private static void onPickup(EntityItemPickupEvent event) {
        if (event.isCanceled() || event.getResult() == Event.Result.DENY) {
            return;
        }
        ItemEntity entity = event.getItem();
        // Vanilla only checks an item's target after this event, so check it here as it would.
        boolean allowed = event.getResult() == Event.Result.ALLOW
                || (entity.target == null || entity.target.equals(event.getEntity().getUUID()));
        if (allowed && BackpackHooks.onItemPickup(event.getEntity(), entity)) {
            event.setCanceled(true);
        }
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity entity) {
            BackpackHooks.onItemEntityJoin(entity);
        }
    }
}
