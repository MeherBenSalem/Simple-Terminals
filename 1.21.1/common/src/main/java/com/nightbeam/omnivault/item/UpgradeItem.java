package com.nightbeam.omnivault.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** An upgrade card for network devices (buses, transmitters, interfaces). */
public class UpgradeItem extends Item {
    public enum Kind {
        SPEED(4), STACK(4), FUZZY(1), RANGE(4), REDSTONE(1);

        public final int maxPerDevice;

        Kind(int maxPerDevice) {
            this.maxPerDevice = maxPerDevice;
        }
    }

    private final Kind kind;

    public UpgradeItem(Kind kind, Properties properties) {
        super(properties.stacksTo(16));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.omnivault.upgrade.max", kind.maxPerDevice).withStyle(ChatFormatting.DARK_GRAY));
    }
}
