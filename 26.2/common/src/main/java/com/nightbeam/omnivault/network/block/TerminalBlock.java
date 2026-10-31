package com.nightbeam.omnivault.network.block;

import com.nightbeam.omnivault.platform.services.IPlatformHelper;

/** A storage or crafting terminal; both share {@link TerminalBlockEntity}. */
public class TerminalBlock extends OrientedDeviceBlock {
    public enum Kind {
        STORAGE, CRAFTING
    }

    private final Kind kind;

    public TerminalBlock(Kind kind, IPlatformHelper.BlockEntityFactory<?> factory, Properties properties) {
        super(factory, properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected boolean connectsThroughFront() {
        return false;
    }
}
