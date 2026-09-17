package com.nightbeam.simpleterminals;

import com.nightbeam.simpleterminals.menu.CraftingTerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeNetworking {
    private static final String PROTOCOL_VERSION = "1";
    public static final Identifier SCROLL_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal_scroll");
    public static final Identifier SEARCH_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "crafting_terminal_search");

    private NeoForgeNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Constants.MOD_ID).versioned(PROTOCOL_VERSION);
        registrar.playToServer(ScrollPayload.TYPE, ScrollPayload.CODEC, NeoForgeNetworking::handleScroll);
        registrar.playToServer(SearchPayload.TYPE, SearchPayload.CODEC, NeoForgeNetworking::handleSearch);
    }

    public static void sendScrollToServer(int containerId, int offsetRows) {
        ClientPacketDistributor.sendToServer(new ScrollPayload(containerId, offsetRows));
    }

    public static void sendSearchToServer(int containerId, String query) {
        ClientPacketDistributor.sendToServer(new SearchPayload(containerId, query));
    }

    public record ScrollPayload(int containerId, int offsetRows) implements CustomPacketPayload {
        public static final Type<ScrollPayload> TYPE = new Type<>(SCROLL_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ScrollPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.containerId);
                    buf.writeVarInt(payload.offsetRows);
                },
                buf -> new ScrollPayload(buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SearchPayload(int containerId, String query) implements CustomPacketPayload {
        public static final Type<SearchPayload> TYPE = new Type<>(SEARCH_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SearchPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.containerId);
                    buf.writeUtf(payload.query, 50);
                },
                buf -> new SearchPayload(buf.readVarInt(), buf.readUtf(50)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void handleScroll(ScrollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                CraftingTerminalMenu.handleScroll(context.player(), payload.containerId(), payload.offsetRows());
            }
        });
    }

    private static void handleSearch(SearchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                CraftingTerminalMenu.handleSearch(context.player(), payload.containerId(), payload.query());
            }
        });
    }
}
