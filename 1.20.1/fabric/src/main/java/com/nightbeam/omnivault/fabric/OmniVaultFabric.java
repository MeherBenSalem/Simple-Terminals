package com.nightbeam.omnivault.fabric;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.backpack.BackpackHooks;
import com.nightbeam.omnivault.drawer.DrawerInteraction;
import com.nightbeam.omnivault.fabric.transfer.FabricStorageExposure;
import com.nightbeam.omnivault.packet.PacketCodec;
import com.nightbeam.omnivault.packet.PacketRegistrar;
import com.nightbeam.omnivault.packet.Payload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class OmniVaultFabric implements ModInitializer {
    /** Codecs of every payload, by type id, for sending. */
    private static final Map<Payload.Type<?>, PacketCodec<?>> CODECS = new HashMap<>();
    /** Client-bound payload handlers, registered by the client entrypoint. */
    private static final List<ClientHandler<?>> CLIENT_HANDLERS = new ArrayList<>();

    private record ClientHandler<T extends Payload>(Payload.Type<T> type, PacketCodec<T> codec, Consumer<T> handler) {
        void registerWith(ClientHandlerSink sink) {
            sink.accept(type, codec, handler);
        }
    }

    /** Receives each client-bound payload type with its codec and handler. */
    public interface ClientHandlerSink {
        <T extends Payload> void accept(Payload.Type<T> type, PacketCodec<T> codec, Consumer<T> handler);
    }

    public static void forEachClientHandler(ClientHandlerSink sink) {
        CLIENT_HANDLERS.forEach(handler -> handler.registerWith(sink));
    }

    /** The codec registered for a payload's type. */
    @SuppressWarnings("unchecked")
    public static <T extends Payload> PacketCodec<T> codec(T payload) {
        PacketCodec<T> codec = (PacketCodec<T>) CODECS.get(payload.type());
        if (codec == null) {
            throw new IllegalArgumentException("Unregistered payload " + payload.type().id());
        }
        return codec;
    }

    @Override
    public void onInitialize() {
        OmniVaultCommon.init();
        FabricRegistryHelper.registerAll();
        LegacyAliases.register();
        registerPayloads();
        FabricStorageExposure.register();
        ServerTickEvents.END_SERVER_TICK.register(OmniVaultCommon::serverTick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item) {
                BackpackHooks.onItemEntityJoin(item);
            }
        });
        // Creative players break blocks on the first left click, before Block#attack runs: a click on
        // a drawer's front extracts instead (on both sides, so the client doesn't predict a break).
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                DrawerInteraction.onLeftClickBlock(player, level, pos, direction) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        OmniVault.LOG.debug("Fabric hooks registered");
    }

    private static void registerPayloads() {
        OmniVaultCommon.registerPackets(new PacketRegistrar() {
            @Override
            public <T extends Payload> void toServer(Payload.Type<T> type, PacketCodec<T> codec, BiConsumer<T, ServerPlayer> handler) {
                CODECS.put(type, codec);
                // Fabric calls receivers on the network thread: decode there, handle on the server thread.
                ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, listener, buf, sender) -> {
                    T payload = codec.decode(buf);
                    server.execute(() -> handler.accept(payload, player));
                });
            }

            @Override
            public <T extends Payload> void toClient(Payload.Type<T> type, PacketCodec<T> codec, Consumer<T> handler) {
                CODECS.put(type, codec);
                CLIENT_HANDLERS.add(new ClientHandler<>(type, codec, handler));
            }
        });
    }
}
