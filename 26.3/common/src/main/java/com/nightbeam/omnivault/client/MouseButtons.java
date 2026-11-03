package com.nightbeam.omnivault.client;

import net.minecraft.client.input.MouseButtonEvent;

/**
 * Minecraft 26.3 reports mouse buttons with SDL numbers (left 1, middle 2, right 3), while the screens compare
 * against the numbers every other version uses (left 0, right 1, middle 2). This translates the same way vanilla's
 * AbstractContainerScreen#getContainerClickButton does before a container click is sent.
 */
public final class MouseButtons {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int MIDDLE = 2;

    private MouseButtons() {
    }

    /** The button as 0 (left), 1 (right) or 2 (middle); other buttons keep their SDL number. */
    public static int of(MouseButtonEvent event) {
        return switch (event.button()) {
            case 1 -> LEFT;
            case 3 -> RIGHT;
            default -> event.button();
        };
    }
}
