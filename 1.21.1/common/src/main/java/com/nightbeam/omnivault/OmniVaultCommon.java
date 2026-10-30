package com.nightbeam.omnivault;

import com.nightbeam.omnivault.config.OVConfig;
import com.nightbeam.omnivault.content.CoreContent;
import com.nightbeam.omnivault.packet.PacketRegistrar;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.util.InventorySorting;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Loader-neutral entrypoint. Each loader calls {@link #init()} from its mod constructor (before its
 * registries fire), {@link #registerPackets(PacketRegistrar)} when payloads are registered and the
 * tick hooks below from its own events.
 */
public final class OmniVaultCommon {
    private static final List<Consumer<MinecraftServer>> SERVER_TICK = new ArrayList<>();
    private static final List<Consumer<PacketRegistrar>> PACKETS = new ArrayList<>();
    private static boolean initialised;

    private OmniVaultCommon() {
    }

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        OmniVault.LOG.info("Initializing {} on {}", OmniVault.MOD_NAME, Services.PLATFORM.getPlatformName());
        // Feature content classes register their entries from static initialisers.
        CoreContent.init();
        com.nightbeam.omnivault.drawer.DrawerContent.init();
        InventorySorting.init();
        OVConfig.load(Services.PLATFORM.getConfigDir());
    }

    /** Features add a callback that runs at the end of every server tick. */
    public static void onServerTick(Consumer<MinecraftServer> callback) {
        SERVER_TICK.add(callback);
    }

    /** Features add their payloads here during {@link #init()}. */
    public static void packets(Consumer<PacketRegistrar> registration) {
        PACKETS.add(registration);
    }

    public static void registerPackets(PacketRegistrar registrar) {
        PACKETS.forEach(p -> p.accept(registrar));
    }

    public static void serverTick(MinecraftServer server) {
        for (Consumer<MinecraftServer> callback : SERVER_TICK) {
            callback.accept(server);
        }
    }
}
