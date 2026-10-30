package com.nightbeam.omnivault.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Loader-neutral payload registration. Handlers always run on the receiving side's main thread.
 * Client handlers must be written as lambdas that call into a client-only class, never as method
 * references, so dedicated servers never resolve client classes.
 */
public interface PacketRegistrar {
    <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler);

    <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler);
}
