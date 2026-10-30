package com.nightbeam.omnivault.client;

import com.nightbeam.omnivault.backpack.client.BackpackClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Client entrypoint; features add their screens, renderers and key handlers here. */
public final class OmniVaultClient {
    private static final List<Consumer<ClientRegistrar>> REGISTRATIONS = new ArrayList<>();
    private static final List<Runnable> CLIENT_TICK = new ArrayList<>();
    private static boolean initialised;

    private OmniVaultClient() {
    }

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        BackpackClient.init();
    }

    public static void registration(Consumer<ClientRegistrar> registration) {
        REGISTRATIONS.add(registration);
    }

    public static void onClientTick(Runnable callback) {
        CLIENT_TICK.add(callback);
    }

    public static void register(ClientRegistrar registrar) {
        init();
        REGISTRATIONS.forEach(r -> r.accept(registrar));
    }

    public static void clientTick() {
        CLIENT_TICK.forEach(Runnable::run);
    }
}
