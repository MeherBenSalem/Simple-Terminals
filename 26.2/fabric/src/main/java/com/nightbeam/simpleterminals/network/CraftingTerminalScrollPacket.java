package com.nightbeam.simpleterminals.network;

import com.nightbeam.simpleterminals.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CraftingTerminalScrollPacket(int containerId, int offsetRows) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal_scroll");
    public static final Type<CraftingTerminalScrollPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, CraftingTerminalScrollPacket> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.containerId);
                buf.writeVarInt(payload.offsetRows);
            },
            buf -> new CraftingTerminalScrollPacket(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
