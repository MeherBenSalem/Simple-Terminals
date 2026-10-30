package com.nightbeam.omnivault.backpack;

import com.nightbeam.omnivault.config.OVConfig;

/** Backpack settings in {@code config/omnivault-common.toml}, section {@code [backpacks]}. */
public final class BackpackConfig {
    private static final String SECTION = "backpacks";

    public static final OVConfig.Value<Integer> MAGNET_DEFAULT_RANGE = OVConfig.integer(SECTION, "magnetDefaultRange", 5, 1, 32,
            "Pull range of a freshly crafted Magnet Upgrade, in blocks");
    public static final OVConfig.Value<Integer> MAGNET_MAX_RANGE = OVConfig.integer(SECTION, "magnetMaxRange", 8, 1, 32,
            "Largest range a player can set on a Magnet Upgrade, in blocks");
    public static final OVConfig.Value<Integer> MAGNET_INTERVAL = OVConfig.integer(SECTION, "magnetInterval", 2, 1, 40,
            "Ticks between Magnet Upgrade pulls");
    public static final OVConfig.Value<Boolean> MAGNET_PULLS_XP = OVConfig.bool(SECTION, "magnetPullsExperience", true,
            "Whether the Magnet Upgrade also pulls experience orbs to the player");
    public static final OVConfig.Value<Integer> FEEDING_INTERVAL = OVConfig.integer(SECTION, "feedingInterval", 20, 1, 1200,
            "Ticks between Feeding Upgrade checks");
    public static final OVConfig.Value<Integer> RESTOCK_INTERVAL = OVConfig.integer(SECTION, "restockInterval", 10, 1, 1200,
            "Ticks between Restock Upgrade checks");
    public static final OVConfig.Value<Integer> COMPACTING_INTERVAL = OVConfig.integer(SECTION, "compactingInterval", 20, 1, 1200,
            "Ticks between Compacting Upgrade passes (only runs after the contents changed)");
    public static final OVConfig.Value<Boolean> PLACED_UPGRADES = OVConfig.bool(SECTION, "placedBackpacksRunUpgrades", true,
            "Whether placed backpacks run their Magnet, Pickup, Void and Compacting upgrades");

    private BackpackConfig() {
    }

    /** Loads this class so its values are registered before the config file is read. */
    public static void init() {
    }
}
