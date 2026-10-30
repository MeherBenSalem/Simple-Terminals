package com.nightbeam.omnivault.fabric;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.backpack.BackpackHooks;
import com.nightbeam.omnivault.drawer.DrawerInteraction;
import com.nightbeam.omnivault.fabric.transfer.FabricStorageExposure;
import com.nightbeam.omnivault.packet.PacketRegistrar;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class OmniVaultFabric implements ModInitializer {
    /** Client-bound payload handlers, registered by the client entrypoint. */
    private static final List<ClientHandler<?>> CLIENT_HANDLERS = new ArrayList<>();

    private record ClientHandler<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        void registerWith(ClientHandlerSink sink) {
            sink.accept(type, handler);
        }
    }

    /** Receives each client-bound payload type with its handler. */
    public interface ClientHandlerSink {
        <T extends CustomPacketPayload> void accept(CustomPacketPayload.Type<T> type, Consumer<T> handler);
    }

    public static void forEachClientHandler(ClientHandlerSink sink) {
        CLIENT_HANDLERS.forEach(handler -> handler.registerWith(sink));
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
            public <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
                PayloadTypeRegistry.playC2S().register(type, codec);
                // Fabric runs play payload handlers on the server thread.
                ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload, context.player()));
            }

            @Override
            public <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
                PayloadTypeRegistry.playS2C().register(type, codec);
                CLIENT_HANDLERS.add(new ClientHandler<>(type, handler));
            }
        });
    }
}
