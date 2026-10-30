package com.nightbeam.omnivault.drawer;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.List;

/**
 * The front of a drawer: how many compartments it has, where each one sits and where the renderer
 * draws its item and count. Coordinates are "face pixels": 0..16, measured from the top-left corner
 * of the front face as the player sees it. {@code tools/assets/drawers.py} builds the models from the
 * same numbers, so keep the two in sync.
 */
public enum DrawerLayout implements StringRepresentable {
    SINGLE("single", List.of(
            new Compartment(1.5f, 1.5f, 14.5f, 14.5f, 8f, 7.5f, 6.5f, 8f, 12.5f, 1.2f, 6f))),
    DOUBLE("double", List.of(
            wide(1.5f),
            wide(9.5f))),
    QUAD("quad", List.of(
            small(4f, 1.5f),
            small(12f, 1.5f),
            small(4f, 9.5f),
            small(12f, 9.5f))),
    COMPACTING("compacting", List.of(
            wide(1.5f),
            small(4f, 9.5f),
            small(12f, 9.5f)));

    public static final Codec<DrawerLayout> CODEC = StringRepresentable.fromEnum(DrawerLayout::values);

    /** Depth (in pixels behind the front plane) of the compartment panels, label plates and frame. */
    public static final float PANEL_DEPTH = 1.0f;
    public static final float LABEL_DEPTH = 0.75f;

    private final String name;
    private final List<Compartment> compartments;

    DrawerLayout(String name, List<Compartment> compartments) {
        this.name = name;
        this.compartments = compartments;
    }

    private static Compartment wide(float top) {
        return new Compartment(1.5f, top, 14.5f, top + 5f, 8f, top + 2.0f, 3.4f, 8f, top + 4.25f, 0.7f, 4.6f);
    }

    private static Compartment small(float centerU, float top) {
        return new Compartment(centerU - 2.5f, top, centerU + 2.5f, top + 5f, centerU, top + 2.35f, 2.7f, centerU, top + 4.275f, 0.65f, 3.8f);
    }

    public int compartments() {
        return compartments.size();
    }

    public Compartment compartment(int index) {
        return compartments.get(index);
    }

    /** Which compartment a point on the front face belongs to (face pixels). */
    public int compartmentAt(double u, double v) {
        boolean top = v < 8;
        boolean left = u < 8;
        return switch (this) {
            case SINGLE -> 0;
            case DOUBLE -> top ? 0 : 1;
            case QUAD -> (top ? 0 : 2) + (left ? 0 : 1);
            case COMPACTING -> top ? 0 : left ? 1 : 2;
        };
    }

    /** Stacks one compartment holds before upgrades (for compacting drawers: stacks of the top tier). */
    public int baseStacks() {
        return switch (this) {
            case SINGLE -> DrawerConfig.SINGLE_STACKS.get();
            case DOUBLE -> DrawerConfig.DOUBLE_STACKS.get();
            case QUAD -> DrawerConfig.QUAD_STACKS.get();
            case COMPACTING -> DrawerConfig.COMPACTING_STACKS.get();
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /**
     * One compartment's front: the drawer panel rectangle, the item icon's centre and size, and the
     * count label's centre, text height and widest allowed text, all in face pixels.
     */
    public record Compartment(float u0, float v0, float u1, float v1, float itemU, float itemV, float itemSize,
                              float labelU, float labelV, float textHeight, float labelWidth) {
    }
}
