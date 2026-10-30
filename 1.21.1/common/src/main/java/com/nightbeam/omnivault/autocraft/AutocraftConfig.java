package com.nightbeam.omnivault.autocraft;

import com.nightbeam.omnivault.config.OVConfig;

public final class AutocraftConfig {
    public static final OVConfig.Value<Integer> ASSEMBLER_INTERVAL = OVConfig.integer("autocrafting", "assemblerInterval", 10, 1, 1200,
            "Ticks between an Assembler's crafting cycles");
    public static final OVConfig.Value<Integer> ASSEMBLER_CRAFTS = OVConfig.integer("autocrafting", "assemblerCraftsPerCycle", 2, 1, 1024,
            "Crafts an Assembler performs per cycle without Speed Upgrades (each upgrade doubles it)");
    public static final OVConfig.Value<Integer> PROVIDER_PUSHES = OVConfig.integer("autocrafting", "providerRunsPerTick", 16, 1, 4096,
            "Most processing-pattern runs a Pattern Provider hands to its machine per tick");

    private AutocraftConfig() {
    }

    public static void init() {
    }
}
