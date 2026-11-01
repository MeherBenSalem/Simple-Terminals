package com.nightbeam.omnivault.autocraft.plan;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Why a craft request cannot run. Each has a {@code gui.omnivault.craft.error.*} message. */
public enum PlanError {
    OFFLINE, NOT_CRAFTABLE, NO_PROCESSOR, PROCESSORS_BUSY, MISSING, TOO_COMPLEX, RESERVE_FAILED, BAD_AMOUNT;

    public Component message() {
        return Component.translatable("gui.omnivault.craft.error." + name().toLowerCase(Locale.ROOT));
    }
}
