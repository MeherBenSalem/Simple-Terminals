package com.nightbeam.omnivault.fabric;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.platform.services.IRegistryHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fabric registers straight into the vanilla registries. An entry is created and registered the first
 * time anything asks for it, so entries that need each other (a block item and its block, a block
 * entity type and its blocks, an item and its data components) work in any declaration order;
 * {@link #registerAll()} then registers whatever nothing has asked for yet.
 */
public class FabricRegistryHelper implements IRegistryHelper {
    private static final List<Entry<?, ?>> ENTRIES = new ArrayList<>();

    @Override
    public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
        Entry<R, T> entry = new Entry<>(registry, name, factory);
        ENTRIES.add(entry);
        return entry;
    }

    /** Called once from the mod initializer, after the common content classes have declared everything. */
    public static void registerAll() {
        for (Entry<?, ?> entry : List.copyOf(ENTRIES)) {
            entry.get();
        }
    }

    private static final class Entry<R, T extends R> implements Supplier<T> {
        private final ResourceKey<? extends Registry<R>> registry;
        private final String name;
        private final Supplier<T> factory;
        private T value;

        Entry(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
            this.registry = registry;
            this.name = name;
            this.factory = factory;
        }

        @Override
        @SuppressWarnings("unchecked")
        public synchronized T get() {
            if (value == null) {
                Registry<R> target = (Registry<R>) BuiltInRegistries.REGISTRY.get(registry.location());
                if (target == null) {
                    throw new IllegalStateException("Unknown registry " + registry.location() + " for " + name);
                }
                T created = factory.get();
                Registry.register(target, OmniVault.id(name), created);
                value = created;
            }
            return value;
        }
    }
}
