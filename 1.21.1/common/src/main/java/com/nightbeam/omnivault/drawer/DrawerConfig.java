package com.nightbeam.omnivault.drawer;

import com.nightbeam.omnivault.config.OVConfig;

/** Drawer settings, written to the {@code [drawers]} section of {@code omnivault-common.toml}. */
public final class DrawerConfig {
    private static final String SECTION = "drawers";

    public static final OVConfig.Value<Integer> SINGLE_STACKS = OVConfig.integer(SECTION, "single_capacity_stacks", 32, 1, 1 << 20,
            "Stacks the compartment of a 1x1 drawer holds without upgrades");
    public static final OVConfig.Value<Integer> DOUBLE_STACKS = OVConfig.integer(SECTION, "double_capacity_stacks", 16, 1, 1 << 20,
            "Stacks each compartment of a 1x2 drawer holds without upgrades");
    public static final OVConfig.Value<Integer> QUAD_STACKS = OVConfig.integer(SECTION, "quad_capacity_stacks", 8, 1, 1 << 20,
            "Stacks each compartment of a 2x2 drawer holds without upgrades");
    public static final OVConfig.Value<Integer> COMPACTING_STACKS = OVConfig.integer(SECTION, "compacting_capacity_stacks", 8, 1, 1 << 20,
            "Stacks of its most compressed item a compacting drawer holds without upgrades");
    public static final OVConfig.Value<Integer> CONTROLLER_RANGE = OVConfig.integer(SECTION, "controller_range", 12, 1, 64,
            "How far a drawer controller reaches along each axis through touching drawers and trims");
    public static final OVConfig.Value<Integer> CONTROLLER_MAX_BLOCKS = OVConfig.integer(SECTION, "controller_max_blocks", 1024, 1, 16384,
            "Most drawers and trims one drawer controller connects");
    public static final OVConfig.Value<Integer> DOUBLE_CLICK_TICKS = OVConfig.integer(SECTION, "double_click_ticks", 10, 1, 40,
            "A second right-click within this many ticks inserts every matching item from the inventory");

    private DrawerConfig() {
    }

    /** Loads the class so the values above are declared before the config file is read. */
    public static void init() {
    }
}
