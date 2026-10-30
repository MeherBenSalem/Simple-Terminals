package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.fabric.transfer.ItemStorageSlotAccess;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
}
