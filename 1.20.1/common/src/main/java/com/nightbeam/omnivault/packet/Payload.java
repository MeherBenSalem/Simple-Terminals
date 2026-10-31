package com.nightbeam.omnivault.packet;

import net.minecraft.resources.ResourceLocation;

/**
 * A packet's contents, sent with the codec registered for its {@link Type} through {@link PacketRegistrar}.
 * Minecraft 1.20.1 has no custom payload records of its own, so each loader carries these over its
 * own channel (Forge: a {@code SimpleChannel}; Fabric: one channel per type id).
 */
public interface Payload {
    Type<? extends Payload> type();

    /** Identifies a payload; the id is also the Fabric channel name. */
    record Type<T extends Payload>(ResourceLocation id) {
    }
}
