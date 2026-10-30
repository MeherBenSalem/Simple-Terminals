package com.nightbeam.omnivault.drawer;

import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.util.Formatting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

/** A drawer's item: keeps contents, lock and upgrades when broken, and lists them in the tooltip. */
public class DrawerBlockItem extends BlockItem {
    private final DrawerLayout layout;

    public DrawerBlockItem(DrawerBlock block, Properties properties) {
        super(block, properties);
        this.layout = block.layout();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean any = false;
        DrawerContents contents = stack.get(DrawerContent.DRAWER_CONTENTS.get());
        if (contents != null) {
            for (DrawerContents.Entry entry : contents.entries()) {
                if (entry.key().isPresent()) {
                    tooltip.add(line(entry.key().get(), entry.count()));
                    any = true;
                }
            }
        }
        CompactingContents compacting = stack.get(DrawerContent.COMPACTING_CONTENTS.get());
        if (compacting != null) {
            for (CompactingContents.Tier tier : compacting.tiers()) {
                tooltip.add(line(tier.key(), compacting.amount() / Math.max(1, tier.unit())));
                any = true;
            }
        }
        if (!any) {
            tooltip.add(Component.translatable(layout == DrawerLayout.COMPACTING ? "tooltip.omnivault.drawer.capacity_compacting"
                            : "tooltip.omnivault.drawer.capacity", layout.compartments(), layout.baseStacks())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (Boolean.TRUE.equals(stack.get(DrawerContent.DRAWER_LOCKED.get()))) {
            tooltip.add(Component.translatable("tooltip.omnivault.drawer.locked").withStyle(ChatFormatting.GOLD));
        }
        ItemContainerContents upgrades = stack.get(DrawerContent.DRAWER_UPGRADES.get());
        if (upgrades != null) {
            for (ItemStack upgrade : upgrades.nonEmptyItems()) {
                tooltip.add(Component.literal(" + ").append(upgrade.getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    private static Component line(ItemKey key, long count) {
        MutableComponent text = Component.literal(Formatting.full(count) + " × ").withStyle(ChatFormatting.WHITE);
        return text.append(key.view().getHoverName().copy().withStyle(ChatFormatting.GRAY));
    }
}
