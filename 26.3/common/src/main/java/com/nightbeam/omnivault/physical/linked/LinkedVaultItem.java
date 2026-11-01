package com.nightbeam.omnivault.physical.linked;

import com.nightbeam.omnivault.physical.PhysicalContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** The Linked Vault block item; remembers its frequency when broken and placed again. */
public class LinkedVaultItem extends BlockItem {
    public LinkedVaultItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        stack.getOrDefault(PhysicalContent.LINKED_FREQUENCY.get(), LinkedFrequency.DEFAULT).appendTooltip(tooltip);
        tooltip.accept(Component.translatable("block.omnivault.linked_vault.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
