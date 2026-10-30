package com.nightbeam.omnivault.neoforge;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.platform.services.IRegistryHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class NeoForgeRegistryHelper implements IRegistryHelper {
    private static final Map<ResourceKey<?>, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();
    private static IEventBus bus;

    @Override
    @SuppressWarnings("unchecked")
    public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
        DeferredRegister<R> register = (DeferredRegister<R>) REGISTERS.computeIfAbsent(registry, key -> {
            DeferredRegister<R> created = DeferredRegister.create((ResourceKey<? extends Registry<R>>) key, OmniVault.MOD_ID);
            if (bus != null) {
                created.register(bus);
            }
            return created;
        });
        return register.register(name, factory);
    }

    /** Attaches every register created so far, and any created later, to the mod bus. */
    public static void attach(IEventBus modBus) {
        bus = modBus;
        REGISTERS.values().forEach(register -> register.register(modBus));
    }
}
