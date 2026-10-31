package com.nightbeam.omnivault;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OmniVault {
    public static final String MOD_ID = "omnivault";
    public static final String MOD_NAME = "OmniVault";
    /** Namespace of the mod this project was called before 2.0; its block IDs are aliased on load. */
    public static final String LEGACY_MOD_ID = "simple_terminals";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private OmniVault() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
