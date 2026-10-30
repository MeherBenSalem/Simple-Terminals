package com.nightbeam.omnivault.network.api;

import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Set;

/**
 * Bridge from terminals to autocrafting. The autocrafting feature installs its implementation;
 * without it nothing is craftable.
 */
public final class CraftingHooks {
    private static Provider provider = new Provider() {
    };

    private CraftingHooks() {
    }

    public static void install(Provider implementation) {
        provider = implementation;
    }

    public static Provider get() {
        return provider;
    }

    public interface Provider {
        /** Item types the network can craft on request. */
        default Set<ItemKey> craftables(StorageNetwork network) {
            return Collections.emptySet();
        }

        /** Opens the "how many?" request flow for a craftable item. */
        default void requestCraft(ServerPlayer player, StorageNetwork network, ItemKey key, int containerId) {
        }

        /** Called every network tick so jobs can make progress. */
        default void tick(StorageNetwork network, long gameTime) {
        }
    }
}
