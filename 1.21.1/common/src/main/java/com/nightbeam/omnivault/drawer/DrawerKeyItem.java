package com.nightbeam.omnivault.drawer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Locks and unlocks drawers (and every drawer on a controller). The blocks handle the click. */
public class DrawerKeyItem extends Item {
    public DrawerKeyItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.omnivault.drawer_key.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.omnivault.drawer_key.tooltip.controller").withStyle(ChatFormatting.DARK_GRAY));
    }
}
