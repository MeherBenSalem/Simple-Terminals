package com.nightbeam.omnivault.physical;

import com.nightbeam.omnivault.config.OVConfig;

/** Config for vaults, linked storage, the trash can and the Relocator ({@code [physical.*]} sections). */
public final class PhysicalConfig {
    public static final OVConfig.Value<Boolean> VANILLA_KITS = OVConfig.bool("physical.vaults", "kits_upgrade_vanilla", true,
            "Allow the Copper Vault Kit to upgrade vanilla chests and barrels in place");
    public static final OVConfig.Value<Boolean> LINKED_PRIVATE = OVConfig.bool("physical.linked", "allow_private", true,
            "Allow Linked Vaults and Linked Pouches to use private (owner-only) frequencies");
    public static final OVConfig.Value<Boolean> RELOCATOR_ENABLED = OVConfig.bool("physical.relocator", "enabled", true,
            "Allow the Relocator to pick up blocks that have a block entity (blocks in #omnivault:relocator_denylist are always refused)");
    public static final OVConfig.Value<Integer> RELOCATOR_USES = OVConfig.integer("physical.relocator", "uses", 16, 1, 4096,
            "How many moves a Relocator lasts before it breaks (the Netherite Relocator never breaks)");

    private PhysicalConfig() {
    }

    /** Touching the class registers the values before the config file is read. */
    static void init() {
    }
}
