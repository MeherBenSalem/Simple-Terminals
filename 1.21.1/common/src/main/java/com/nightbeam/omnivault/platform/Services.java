package com.nightbeam.omnivault.platform;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.platform.services.IPlatformHelper;
import com.nightbeam.omnivault.platform.services.IRegistryHelper;

import java.util.ServiceLoader;

public final class Services {
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final IRegistryHelper REGISTRY = load(IRegistryHelper.class);

    private Services() {
    }

    public static <T> T load(Class<T> clazz) {
        T service = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        OmniVault.LOG.debug("Loaded {} for service {}", service, clazz);
        return service;
    }
}
