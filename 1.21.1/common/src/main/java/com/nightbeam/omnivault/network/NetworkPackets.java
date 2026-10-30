package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.network.client.NetworkClientHandlers;
import com.nightbeam.omnivault.network.item.WirelessTerminalItem;
import com.nightbeam.omnivault.network.menu.TerminalMenu;
import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Map;

/** Payloads for terminals and network screens. */
public final class NetworkPackets {
    private NetworkPackets() {
    }

    /** One grid entry: count 0 and not craftable means "remove". */
    public record GridEntry(ItemKey key, long count, boolean craftable) {
        public static final StreamCodec<RegistryFriendlyByteBuf, GridEntry> CODEC = StreamCodec.composite(
                ItemKey.STREAM_CODEC, GridEntry::key,
                ByteBufCodecs.VAR_LONG, GridEntry::count,
                ByteBufCodecs.BOOL, GridEntry::craftable,
                GridEntry::new);
    }

    /** Server → client: changes to a terminal's view of the network. */
    public record GridContents(int containerId, boolean reset, boolean online, List<GridEntry> entries) implements CustomPacketPayload {
        public static final Type<GridContents> TYPE = new Type<>(OmniVault.id("grid_contents"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GridContents> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GridContents::containerId,
                ByteBufCodecs.BOOL, GridContents::reset,
                ByteBufCodecs.BOOL, GridContents::online,
                GridEntry.CODEC.apply(ByteBufCodecs.list()), GridContents::entries,
                GridContents::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: the network's fluids (millibuckets); amount 0 removes an entry. */
    public record FluidContents(int containerId, boolean reset, Map<FluidKey, Long> fluids) implements CustomPacketPayload {
        public static final Type<FluidContents> TYPE = new Type<>(OmniVault.id("fluid_contents"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidContents> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FluidContents::containerId,
                ByteBufCodecs.BOOL, FluidContents::reset,
                ByteBufCodecs.map(java.util.HashMap::new, FluidKey.STREAM_CODEC, ByteBufCodecs.VAR_LONG), FluidContents::fluids,
                FluidContents::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: fill the carried empty bucket with a fluid, or empty the carried bucket (key absent). */
    public record FluidClick(int containerId, java.util.Optional<FluidKey> fill) implements CustomPacketPayload {
        public static final Type<FluidClick> TYPE = new Type<>(OmniVault.id("fluid_click"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidClick> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FluidClick::containerId,
                ByteBufCodecs.optional(FluidKey.STREAM_CODEC), FluidClick::fill,
                FluidClick::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: a click on a grid entry. */
    public record GridClick(int containerId, ItemKey key, int action) implements CustomPacketPayload {
        public static final Type<GridClick> TYPE = new Type<>(OmniVault.id("grid_click"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GridClick> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GridClick::containerId,
                ItemKey.STREAM_CODEC, GridClick::key,
                ByteBufCodecs.VAR_INT, GridClick::action,
                GridClick::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: drop the carried stack (or one item of it) into the network. */
    public record GridInsert(int containerId, boolean single) implements CustomPacketPayload {
        public static final Type<GridInsert> TYPE = new Type<>(OmniVault.id("grid_insert"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GridInsert> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GridInsert::containerId,
                ByteBufCodecs.BOOL, GridInsert::single,
                GridInsert::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: set a numeric setting on the open menu (thresholds, amounts). */
    public record MenuValue(int containerId, int key, long value) implements CustomPacketPayload {
        public static final Type<MenuValue> TYPE = new Type<>(OmniVault.id("menu_value"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MenuValue> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, MenuValue::containerId,
                ByteBufCodecs.VAR_INT, MenuValue::key,
                ByteBufCodecs.VAR_LONG, MenuValue::value,
                MenuValue::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: Nexus Core overview numbers. */
    public record CoreStats(int containerId, int state, int nodes, int types, long stored, long capacity,
                            long energy, long energyMax, int usage, Map<String, Integer> devices) implements CustomPacketPayload {
        public static final Type<CoreStats> TYPE = new Type<>(OmniVault.id("core_stats"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CoreStats> CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeVarInt(s.containerId);
                    buf.writeVarInt(s.state);
                    buf.writeVarInt(s.nodes);
                    buf.writeVarInt(s.types);
                    buf.writeVarLong(s.stored);
                    buf.writeVarLong(s.capacity);
                    buf.writeVarLong(s.energy);
                    buf.writeVarLong(s.energyMax);
                    buf.writeVarInt(s.usage);
                    buf.writeMap(s.devices, (b, k) -> b.writeUtf(k), (b, v) -> b.writeVarInt(v));
                },
                buf -> new CoreStats(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                        buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
                        buf.readMap(b -> b.readUtf(), b -> b.readVarInt())));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: the "open wireless terminal" key was pressed. */
    public record OpenWireless() implements CustomPacketPayload {
        public static final Type<OpenWireless> TYPE = new Type<>(OmniVault.id("open_wireless"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenWireless> CODEC = StreamCodec.unit(new OpenWireless());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Menus that accept {@link MenuValue}. */
    public interface ValueReceiver {
        void receiveValue(int key, long value);
    }

    public static void init() {
        OmniVaultCommon.packets(registrar -> {
            registrar.toClient(GridContents.TYPE, GridContents.CODEC, payload -> NetworkClientHandlers.gridContents(payload));
            registrar.toClient(FluidContents.TYPE, FluidContents.CODEC, payload -> NetworkClientHandlers.fluidContents(payload));
            registrar.toServer(FluidClick.TYPE, FluidClick.CODEC, (payload, player) -> {
                if (player.containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId()) {
                    menu.handleFluidClick(player, payload.fill().orElse(null));
                }
            });
            registrar.toClient(CoreStats.TYPE, CoreStats.CODEC, payload -> NetworkClientHandlers.coreStats(payload));
            registrar.toServer(GridClick.TYPE, GridClick.CODEC, (payload, player) -> {
                if (player.containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId()) {
                    menu.handleClick(player, payload.key(), payload.action());
                }
            });
            registrar.toServer(GridInsert.TYPE, GridInsert.CODEC, (payload, player) -> {
                if (player.containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId()) {
                    menu.handleInsertCarried(player, payload.single());
                }
            });
            registrar.toServer(OpenWireless.TYPE, OpenWireless.CODEC, (payload, player) -> {
                // Off hand first, then the hotbar and main inventory.
                if (player.getInventory().getItem(40).getItem() instanceof WirelessTerminalItem) {
                    WirelessTerminalItem.open(player, 40);
                    return;
                }
                for (int slot = 0; slot < 36; slot++) {
                    if (player.getInventory().getItem(slot).getItem() instanceof WirelessTerminalItem) {
                        WirelessTerminalItem.open(player, slot);
                        return;
                    }
                }
            });
            registrar.toServer(MenuValue.TYPE, MenuValue.CODEC, (payload, player) -> {
                if (player.containerMenu instanceof ValueReceiver receiver && player.containerMenu.containerId == payload.containerId()
                        && player.containerMenu.stillValid(player)) {
                    receiver.receiveValue(payload.key(), payload.value());
                }
            });
        });
    }
}
