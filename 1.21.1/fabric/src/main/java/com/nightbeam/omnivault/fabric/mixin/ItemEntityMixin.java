package com.nightbeam.omnivault.fabric.mixin;

import com.nightbeam.omnivault.backpack.BackpackHooks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Backpack Pickup and Void upgrades get first pick of items a player walks over (NeoForge: ItemEntityPickupEvent). */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Shadow
    private int pickupDelay;

    @Shadow
    @Nullable
    private UUID target;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void omnivault$backpackPickup(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.level().isClientSide && pickupDelay == 0 && (target == null || target.equals(player.getUUID()))
                && BackpackHooks.onItemPickup(player, self)) {
            ci.cancel();
        }
    }
}
