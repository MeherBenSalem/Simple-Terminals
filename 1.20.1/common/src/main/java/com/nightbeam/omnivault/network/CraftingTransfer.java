package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.network.menu.CraftingTerminalMenu;
import com.nightbeam.omnivault.packet.PacketCodec;
import com.nightbeam.omnivault.packet.PacketCodecs;
import com.nightbeam.omnivault.packet.Payload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client → server: fill the crafting grid of an open crafting terminal with a recipe from a recipe
 * viewer. Each of the nine entries lists the acceptable stacks for that grid slot.
 */
public record CraftingTransfer(int containerId, List<List<ItemStack>> slots) implements Payload {
    public static final int MAX_CANDIDATES = 32;
    public static final Type<CraftingTransfer> TYPE = new Type<>(OmniVault.id("crafting_transfer"));
    public static final PacketCodec<CraftingTransfer> CODEC = PacketCodec.composite(
            PacketCodecs.VAR_INT, CraftingTransfer::containerId,
            PacketCodecs.ITEM_STACK.apply(PacketCodecs.list()).apply(PacketCodecs.list(9)), CraftingTransfer::slots,
            CraftingTransfer::new);

    @Override
    public Type<? extends Payload> type() {
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
