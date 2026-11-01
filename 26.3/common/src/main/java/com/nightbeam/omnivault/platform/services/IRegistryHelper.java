package com.nightbeam.omnivault.platform.services;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.function.Supplier;

/** Loader-neutral registration. Entries are created lazily by the loader at its registration time. */
public interface IRegistryHelper {
    <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory);
}
