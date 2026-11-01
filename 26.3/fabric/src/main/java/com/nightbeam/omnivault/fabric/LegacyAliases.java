package com.nightbeam.omnivault.fabric;

import com.nightbeam.omnivault.OmniVault;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Worlds made with Simple Terminals keep their terminals: the old ID now points at the new block. */
final class LegacyAliases {
    private LegacyAliases() {
    }

    static void register() {
        Identifier legacy = Identifier.fromNamespaceAndPath(OmniVault.LEGACY_MOD_ID, "crafting_terminal");
        Identifier current = OmniVault.id("crafting_terminal");
        BuiltInRegistries.BLOCK.addAlias(legacy, current);
        BuiltInRegistries.ITEM.addAlias(legacy, current);
    }
}
