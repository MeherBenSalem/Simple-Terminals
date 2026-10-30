package com.nightbeam.omnivault.drawer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A drawer upgrade. Capacity upgrades add their multiplier to the drawer's (two Iron upgrades make
 * ×4, Iron + Gold make ×6); a Void upgrade destroys matching items that no longer fit.
 */
public class DrawerUpgradeItem extends Item {
    public enum Kind {
        CAPACITY, VOID
    }

    private final Kind kind;
    private final int multiplier;

    public DrawerUpgradeItem(Kind kind, int multiplier, Properties properties) {
        super(properties.stacksTo(16));
        this.kind = kind;
        this.multiplier = multiplier;
    }

    public Kind kind() {
        return kind;
    }

    /** The capacity multiplier this upgrade adds, or 0 for non-capacity upgrades. */
    public int multiplier() {
        return kind == Kind.CAPACITY ? multiplier : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (kind == Kind.CAPACITY) {
            tooltip.add(Component.translatable("tooltip.omnivault.drawer_upgrade.capacity", multiplier).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.omnivault.drawer_upgrade.void").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.omnivault.drawer_upgrade.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
