package com.nightbeam.omnivault.backpack;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Backpack tiers: storage slots, upgrade slots and how wide the screen's grid is. */
public enum BackpackTier implements StringRepresentable {
    LEATHER(27, 1, 9, Rarity.COMMON, ChatFormatting.GOLD),
    COPPER(36, 2, 9, Rarity.COMMON, ChatFormatting.GOLD),
    IRON(54, 3, 9, Rarity.COMMON, ChatFormatting.GRAY),
    GOLD(81, 4, 12, Rarity.UNCOMMON, ChatFormatting.YELLOW),
    DIAMOND(108, 5, 12, Rarity.RARE, ChatFormatting.AQUA),
    NETHERITE(120, 6, 12, Rarity.EPIC, ChatFormatting.DARK_PURPLE);

    public static final Codec<BackpackTier> CODEC = StringRepresentable.fromEnum(BackpackTier::values);

    private final int slots;
    private final int upgradeSlots;
    private final int columns;
    private final Rarity rarity;
    private final ChatFormatting color;

    BackpackTier(int slots, int upgradeSlots, int columns, Rarity rarity, ChatFormatting color) {
        this.slots = slots;
        this.upgradeSlots = upgradeSlots;
        this.columns = columns;
        this.rarity = rarity;
        this.color = color;
    }

    public int slots() {
        return slots;
    }

    public int upgradeSlots() {
        return upgradeSlots;
    }

    /** Columns in the backpack screen; wider tiers use wider grids and scroll beyond six rows. */
    public int columns() {
        return columns;
    }

    public Rarity rarity() {
        return rarity;
    }

    public ChatFormatting color() {
        return color;
    }

    public boolean fireResistant() {
        return this == NETHERITE;
    }

    /** Registry path of this tier's block and item, e.g. {@code iron_backpack}. */
    public String id() {
        return getSerializedName() + "_backpack";
    }

    @Nullable
    public BackpackTier next() {
        int next = ordinal() + 1;
        return next < values().length ? values()[next] : null;
    }

    public static BackpackTier byOrdinal(int ordinal) {
        BackpackTier[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
