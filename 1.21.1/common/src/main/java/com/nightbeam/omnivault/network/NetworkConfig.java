package com.nightbeam.omnivault.network;

import com.nightbeam.omnivault.config.OVConfig;

public final class NetworkConfig {
    public static final OVConfig.Value<Integer> MAX_NODES = OVConfig.integer("network", "maxNodes", 8192, 64, 1_000_000,
            "Most cables and devices one Nexus Core will scan");
    public static final OVConfig.Value<Boolean> ENERGY = OVConfig.bool("network", "requireEnergy", false,
            "If true, networks need Forge Energy (FE) delivered to the Nexus Core to run");
    public static final OVConfig.Value<Integer> CORE_BUFFER = OVConfig.integer("network", "coreEnergyBuffer", 1_000_000, 1000, Integer.MAX_VALUE,
            "FE the Nexus Core can buffer");
    public static final OVConfig.Value<Integer> CORE_USAGE = OVConfig.integer("network", "coreEnergyUsage", 8, 0, 100_000,
            "FE per tick used by the Nexus Core itself");
    public static final OVConfig.Value<Integer> CELL_TYPES = OVConfig.integer("cells", "typeLimit", 128, 1, 1_000_000,
            "Different item types one storage cell can hold");
    public static final OVConfig.Value<Integer> FLUID_CELL_TYPES = OVConfig.integer("cells", "fluidTypeLimit", 32, 1, 1_000_000,
            "Different fluids one fluid cell can hold");
    public static final OVConfig.Value<Integer> CORE_RANGE = OVConfig.integer("wireless", "coreRange", 32, 0, 1_000_000,
            "Blocks a wireless terminal reaches from the Nexus Core itself");
    public static final OVConfig.Value<Integer> TRANSMITTER_RANGE = OVConfig.integer("wireless", "transmitterRange", 64, 1, 1_000_000,
            "Blocks a Wireless Transmitter reaches without upgrades");
    public static final OVConfig.Value<Integer> RANGE_PER_UPGRADE = OVConfig.integer("wireless", "rangePerUpgrade", 48, 0, 1_000_000,
            "Extra blocks per Range Upgrade in a Wireless Transmitter");
    public static final OVConfig.Value<Integer> BUS_INTERVAL = OVConfig.integer("buses", "baseInterval", 20, 1, 1200,
            "Ticks between import/export bus operations without Speed Upgrades (each upgrade halves it)");
    public static final OVConfig.Value<Integer> BUS_ITEMS = OVConfig.integer("buses", "baseItemsPerOperation", 8, 1, 4096,
            "Items an import/export bus moves per operation without Stack Upgrades (each upgrade doubles it)");

    private NetworkConfig() {
    }

    public static void init() {
    }
}
