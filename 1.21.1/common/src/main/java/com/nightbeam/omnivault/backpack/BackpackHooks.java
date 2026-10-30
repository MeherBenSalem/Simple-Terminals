package com.nightbeam.omnivault.backpack;

import com.nightbeam.omnivault.backpack.upgrade.BackpackUpgrades;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral entry points for game events vanilla has no hook for. Each loader calls these from its
 * own events (NeoForge: {@code ItemEntityPickupEvent.Pre} and {@code EntityJoinLevelEvent}).
 */
public final class BackpackHooks {
    private BackpackHooks() {
    }

    /**
     * A player touches an item entity it is allowed to pick up (no pickup delay, no other owner). Pickup and
     * Void upgrades take what they want, shrinking the entity's stack in place.
     *
     * @return true if nothing is left, so the loader must cancel the vanilla pickup
     */
    public static boolean onItemPickup(Player player, ItemEntity entity) {
        if (player.level().isClientSide || player.isSpectator() || !entity.isAlive() || entity.hasPickUpDelay()) {
            return false;
        }
        ItemStack stack = entity.getItem();
        int before = stack.getCount();
        ItemStack rest = BackpackUpgrades.absorb(player, stack);
        int taken = before - rest.getCount();
        if (taken <= 0) {
            return false;
        }
        player.take(entity, taken);
        player.awardStat(Stats.ITEM_PICKED_UP.get(stack.getItem()), taken);
        stack.setCount(rest.getCount());
        if (stack.isEmpty()) {
            entity.discard();
            return true;
        }
        return false;
    }

    /** An item entity was added to a level: dropped backpacks with an Everlasting Upgrade become indestructible. */
    public static void onItemEntityJoin(ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !Backpacks.isBackpack(entity.getItem())) {
            return;
        }
        BackpackData data = Backpacks.existingData(level.getServer(), entity.getItem());
        if (data != null && BackpackUpgrades.isEverlasting(data)) {
            entity.setUnlimitedLifetime();
            entity.setInvulnerable(true);
        }
    }
}
