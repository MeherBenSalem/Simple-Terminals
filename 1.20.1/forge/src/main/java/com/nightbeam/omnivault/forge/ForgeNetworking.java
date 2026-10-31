package com.nightbeam.omnivault.forge;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.packet.PacketCodec;
import com.nightbeam.omnivault.packet.PacketRegistrar;
import com.nightbeam.omnivault.packet.Payload;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Carries the common payloads over one Forge {@link SimpleChannel}: a message is the payload's type id
 * followed by what its codec writes. Server-bound and client-bound payloads are two message kinds, so
 * each only ever arrives on the side it is meant for.
 */
final class ForgeNetworking {
    private static final String PROTOCOL_VERSION = "2";
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(OmniVault.id("main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
    private static final Map<ResourceLocation, ServerBound<?>> TO_SERVER = new HashMap<>();
    private static final Map<ResourceLocation, ClientBound<?>> TO_CLIENT = new HashMap<>();

    private ForgeNetworking() {
    }

    static void register() {
        OmniVaultCommon.registerPackets(new PacketRegistrar() {
            @Override
            public <T extends Payload> void toServer(Payload.Type<T> type, PacketCodec<T> codec, BiConsumer<T, ServerPlayer> handler) {
                TO_SERVER.put(type.id(), new ServerBound<>(codec, handler));
            }

            @Override
            public <T extends Payload> void toClient(Payload.Type<T> type, PacketCodec<T> codec, Consumer<T> handler) {
                TO_CLIENT.put(type.id(), new ClientBound<>(codec, handler));
            }
        });
        CHANNEL.messageBuilder(ToServer.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToServer::encode).decoder(ToServer::decode).consumerMainThread(ToServer::handle).add();
        CHANNEL.messageBuilder(ToClient.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ToClient::encode).decoder(ToClient::decode).consumerMainThread(ToClient::handle).add();
    }

    static void sendToServer(Payload payload) {
        CHANNEL.sendToServer(new ToServer(payload));
    }

    static void sendToPlayer(ServerPlayer player, Payload payload) {
        // Fake players from other mods (and test players) have no modded connection to talk to.
        Connection connection = player.connection == null ? null : player.connection.connection;
        if (connection != null && connection.channel() != null && CHANNEL.isRemotePresent(connection)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ToClient(payload));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Payload> void write(FriendlyByteBuf buf, Payload payload, PacketCodec<T> codec) {
        buf.writeResourceLocation(payload.type().id());
        codec.encode(buf, (T) payload);
    }

    private record ServerBound<T extends Payload>(PacketCodec<T> codec, BiConsumer<T, ServerPlayer> handler) {
        @SuppressWarnings("unchecked")
        void handle(Payload payload, ServerPlayer player) {
            handler.accept((T) payload, player);
        }
    }

    private record ClientBound<T extends Payload>(PacketCodec<T> codec, Consumer<T> handler) {
        @SuppressWarnings("unchecked")
        void handle(Payload payload) {
            handler.accept((T) payload);
        }
    }

    private record ToServer(Payload payload) {
        void encode(FriendlyByteBuf buf) {
            write(buf, payload, TO_SERVER.get(payload.type().id()).codec());
        }

        static ToServer decode(FriendlyByteBuf buf) {
            ResourceLocation id = buf.readResourceLocation();
            ServerBound<?> registered = TO_SERVER.get(id);
            if (registered == null) {
                throw new IllegalArgumentException("Unknown OmniVault payload " + id);
            }
            return new ToServer(registered.codec().decode(buf));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                TO_SERVER.get(payload.type().id()).handle(payload, player);
            }
        }
    }

    private record ToClient(Payload payload) {
        void encode(FriendlyByteBuf buf) {
            write(buf, payload, TO_CLIENT.get(payload.type().id()).codec());
        }

        static ToClient decode(FriendlyByteBuf buf) {
            ResourceLocation id = buf.readResourceLocation();
            ClientBound<?> registered = TO_CLIENT.get(id);
            if (registered == null) {
                throw new IllegalArgumentException("Unknown OmniVault payload " + id);
            }
            return new ToClient(registered.codec().decode(buf));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            TO_CLIENT.get(payload.type().id()).handle(payload);
        }
    }
}
