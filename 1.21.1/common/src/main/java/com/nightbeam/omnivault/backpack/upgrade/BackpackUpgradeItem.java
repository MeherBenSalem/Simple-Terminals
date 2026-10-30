package com.nightbeam.omnivault.backpack.upgrade;

import com.nightbeam.omnivault.backpack.BackpackContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** An upgrade card installed in a backpack's upgrade slots. Its settings live on the card. */
public class BackpackUpgradeItem extends Item {
    private final UpgradeType type;

    public BackpackUpgradeItem(UpgradeType type, Properties properties) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public UpgradeType type() {
        return type;
    }

    public static UpgradeConfig config(ItemStack stack) {
        UpgradeConfig config = stack.get(BackpackContent.UPGRADE_CONFIG.get());
        return config != null ? config : UpgradeConfig.DEFAULT;
    }

    public static void setConfig(ItemStack stack, UpgradeConfig config) {
        if (config.equals(UpgradeConfig.DEFAULT)) {
            stack.remove(BackpackContent.UPGRADE_CONFIG.get());
        } else {
            stack.set(BackpackContent.UPGRADE_CONFIG.get(), config);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        UpgradeConfig config = config(stack);
        if (!config.enabled()) {
            tooltip.add(Component.translatable("tooltip.omnivault.backpack_upgrade.disabled").withStyle(ChatFormatting.RED));
        }
        if (type.hasFilter() && !config.filterEmpty()) {
            long entries = config.filter().stream().filter(s -> !s.isEmpty()).count();
            tooltip.add(Component.translatable(config.allow() ? "tooltip.omnivault.backpack_upgrade.allow_list" : "tooltip.omnivault.backpack_upgrade.deny_list",
                    entries).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.omnivault.backpack_upgrade.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
