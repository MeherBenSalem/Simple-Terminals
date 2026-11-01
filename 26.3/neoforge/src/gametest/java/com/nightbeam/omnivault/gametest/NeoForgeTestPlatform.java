package com.nightbeam.omnivault.gametest;

import com.nightbeam.omnivault.neoforge.transfer.ItemHandlerSlotAccess;
import com.nightbeam.omnivault.storage.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

public class NeoForgeTestPlatform implements TestPlatform {
    @Nullable
    @Override
    public SlotAccess items(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        return handler == null ? null : new ItemHandlerSlotAccess(handler);
    }

    @Override
    public boolean leftClickBlock(Player player, BlockPos pos, Direction face) {
        return CommonHooks.onLeftClickBlock(player, pos, face, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK).isCanceled();
    }
}
