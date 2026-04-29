package com.nightbeam.simpleterminals;

import com.nightbeam.simpleterminals.menu.CraftingTerminalMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ForgeNetworking {
    private static int nextId;

    public static void register() {
        SimpleTerminals.NETWORK.registerMessage(nextId++, ScrollMessage.class, ScrollMessage::encode, ScrollMessage::decode, ScrollMessage::handle);
        SimpleTerminals.NETWORK.registerMessage(nextId++, SearchMessage.class, SearchMessage::encode, SearchMessage::decode, SearchMessage::handle);
    }

    public static void sendScrollToServer(int containerId, int offsetRows) {
        SimpleTerminals.NETWORK.sendToServer(new ScrollMessage(containerId, offsetRows));
    }

    public static void sendSearchToServer(int containerId, String query) {
        SimpleTerminals.NETWORK.sendToServer(new SearchMessage(containerId, query));
    }

    public record ScrollMessage(int containerId, int offsetRows) {
        public static void encode(ScrollMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.containerId);
            buffer.writeVarInt(message.offsetRows);
        }

        public static ScrollMessage decode(FriendlyByteBuf buffer) {
            return new ScrollMessage(buffer.readVarInt(), buffer.readVarInt());
        }

        public static void handle(ScrollMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                if (context.getSender() != null) {
                    CraftingTerminalMenu.handleScroll(context.getSender(), message.containerId, message.offsetRows);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record SearchMessage(int containerId, String query) {
        public static void encode(SearchMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.containerId);
            buffer.writeUtf(message.query, 50);
        }

        public static SearchMessage decode(FriendlyByteBuf buffer) {
            return new SearchMessage(buffer.readVarInt(), buffer.readUtf(50));
        }

        public static void handle(SearchMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                if (context.getSender() != null) {
                    CraftingTerminalMenu.handleSearch(context.getSender(), message.containerId, message.query);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
