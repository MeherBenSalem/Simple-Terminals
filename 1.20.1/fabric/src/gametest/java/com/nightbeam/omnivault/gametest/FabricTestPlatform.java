package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.fabric.transfer.ItemStorageSlotAccess;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class FabricTestPlatform implements TestPlatform {
    @Nullable
    @Override
    public SlotAccess items(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);
        return storage == null ? null : new ItemStorageSlotAccess(storage);
    }

    @Override
    public boolean leftClickBlock(Player player, BlockPos pos, Direction face) {
        return AttackBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, pos, face) != InteractionResult.PASS;
    }

    @Override
    public boolean extractionCappedAtAStack() {
        return false;
    }

    /** Fabric keeps the old id as a registry alias, which every lookup by name follows. */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T legacyEntry(ResourceKey<? extends Registry<T>> registry, ResourceLocation id) {
        Registry<T> target = (Registry<T>) BuiltInRegistries.REGISTRY.get(registry.location());
        return target == null ? null : target.get(id);
    }
}
