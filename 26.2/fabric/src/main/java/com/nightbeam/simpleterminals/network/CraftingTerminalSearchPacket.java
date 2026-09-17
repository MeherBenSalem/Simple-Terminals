package com.nightbeam.simpleterminals.network;

import com.nightbeam.simpleterminals.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CraftingTerminalSearchPacket(int containerId, String query) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal_search");
    public static final Type<CraftingTerminalSearchPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, CraftingTerminalSearchPacket> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.containerId);
                buf.writeUtf(payload.query, 50);
            },
            buf -> new CraftingTerminalSearchPacket(buf.readVarInt(), buf.readUtf(50)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
