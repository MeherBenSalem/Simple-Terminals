package com.nightbeam.omnivault.backpack;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Where an open backpack lives: in a player inventory slot, or placed as a block. */
public sealed interface BackpackSource {
    /** The backpack stack right now, or {@link ItemStack#EMPTY} if it is gone. */
    ItemStack stack(Player player);

    /** {@link Inventory} slot index (0-35 main, 36-39 armour, 40 off-hand) holding the backpack. */
    record Held(int slot) implements BackpackSource {
        @Override
        public ItemStack stack(Player player) {
            return slot >= 0 && slot < player.getInventory().getContainerSize() ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
        }
    }

    record Placed(BlockPos pos) implements BackpackSource {
        @Override
        public ItemStack stack(Player player) {
            BackpackBlockEntity blockEntity = blockEntity(player.level());
            return blockEntity == null ? ItemStack.EMPTY : blockEntity.getBackpack();
        }

        @Nullable
        public BackpackBlockEntity blockEntity(Level level) {
            return level.getBlockEntity(pos) instanceof BackpackBlockEntity blockEntity ? blockEntity : null;
        }
    }

    static void write(FriendlyByteBuf buf, BackpackSource source) {
        if (source instanceof Placed placed) {
            buf.writeBoolean(true);
            buf.writeBlockPos(placed.pos());
        } else {
            buf.writeBoolean(false);
            buf.writeVarInt(((Held) source).slot());
        }
    }

    static BackpackSource read(FriendlyByteBuf buf) {
        return buf.readBoolean() ? new Placed(buf.readBlockPos()) : new Held(buf.readVarInt());
    }
}
