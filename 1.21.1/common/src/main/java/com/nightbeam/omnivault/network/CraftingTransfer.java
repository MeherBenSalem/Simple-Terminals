package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.network.menu.CraftingTerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client → server: fill the crafting grid of an open crafting terminal with a recipe from a recipe
 * viewer. Each of the nine entries lists the acceptable stacks for that grid slot.
 */
public record CraftingTransfer(int containerId, List<List<ItemStack>> slots) implements CustomPacketPayload {
    public static final int MAX_CANDIDATES = 32;
    public static final Type<CraftingTransfer> TYPE = new Type<>(OmniVault.id("crafting_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingTransfer> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftingTransfer::containerId,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.apply(ByteBufCodecs.list(9)), CraftingTransfer::slots,
            CraftingTransfer::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void init() {
        OmniVaultCommon.packets(registrar -> registrar.toServer(TYPE, CODEC, (payload, player) -> {
            if (player.containerMenu instanceof CraftingTerminalMenu menu && menu.containerId == payload.containerId()
                    && menu.stillValid(player) && payload.slots().size() == 9) {
                menu.transferRecipe(player, payload.slots());
            }
        }));
    }
}
