package com.nightbeam.omnivault.forge;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.network.NetworkContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Worlds made with Simple Terminals keep their terminals: the old ID now points at the new block. */
public final class LegacyMappings {
    static final String CRAFTING_TERMINAL = "crafting_terminal";

    private LegacyMappings() {
    }

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(LegacyMappings::remap);
    }

    private static void remap(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Block> mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, OmniVault.LEGACY_MOD_ID)) {
            if (mapping.getKey().getPath().equals(CRAFTING_TERMINAL)) {
                mapping.remap(NetworkContent.CRAFTING_TERMINAL.get());
            }
        }
        for (MissingMappingsEvent.Mapping<Item> mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, OmniVault.LEGACY_MOD_ID)) {
            if (mapping.getKey().getPath().equals(CRAFTING_TERMINAL)) {
                mapping.remap(NetworkContent.CRAFTING_TERMINAL.get().asItem());
            }
        }
    }
}
