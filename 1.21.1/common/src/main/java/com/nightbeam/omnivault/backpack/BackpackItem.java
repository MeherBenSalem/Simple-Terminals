package com.nightbeam.omnivault.backpack;

import com.nightbeam.omnivault.backpack.upgrade.BackpackUpgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A backpack: right-click to open, wear it in the chest slot, sneak-right-click a block face to place it
 * (or, with a Deposit Upgrade, sneak-right-click a container to empty matching items into it).
 */
public class BackpackItem extends BlockItem implements Equipable {
    /** Whether the client holds shift (for the longer tooltip); set by the client, always false on servers. */
    public static BooleanSupplier shiftDown = () -> false;

    private final BackpackTier tier;

    public BackpackItem(Block block, BackpackTier tier, Properties properties) {
        super(block, properties.stacksTo(1));
        this.tier = tier;
    }

    public BackpackTier tier() {
        return tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) {
            // Plain right-click on a block opens the backpack (handled by use()).
            return InteractionResult.PASS;
        }
        InteractionResult deposit = BackpackUpgrades.tryDeposit(context);
        if (deposit != InteractionResult.PASS) {
            return deposit;
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Backpacks.OFFHAND_SLOT;
            Backpacks.open(serverPlayer, new BackpackSource.Held(slot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level instanceof ServerLevel serverLevel && entity instanceof Player player) {
            BackpackUpgrades.tickCarried(stack, serverLevel, player);
        }
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.CHEST;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.omnivault.backpack.tier",
                Component.translatable("tooltip.omnivault.backpack.tier." + tier.getSerializedName()).withStyle(tier.color())).withStyle(ChatFormatting.GRAY));
        BackpackSummary summary = stack.getOrDefault(BackpackContent.SUMMARY.get(), BackpackSummary.EMPTY);
        int slots = Math.max(summary.slots(), tier.slots());
        ChatFormatting fill = summary.used() >= slots ? ChatFormatting.RED : summary.used() * 4 >= slots * 3 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        tooltip.add(Component.translatable("tooltip.omnivault.backpack.slots",
                Component.literal(Integer.toString(summary.used())).withStyle(fill), slots).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.omnivault.backpack.upgrades", summary.upgrades().size(), tier.upgradeSlots()).withStyle(ChatFormatting.GRAY));
        for (Item upgrade : summary.upgrades()) {
            tooltip.add(Component.literal("  ◆ ").withStyle(ChatFormatting.DARK_PURPLE)
                    .append(Component.translatable(upgrade.getDescriptionId()).withStyle(ChatFormatting.AQUA)));
        }
        if (shiftDown.getAsBoolean()) {
            tooltip.add(Component.translatable("tooltip.omnivault.backpack.help.open").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.omnivault.backpack.help.wear",
                    Component.keybind("key.omnivault.open_backpack")).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.omnivault.backpack.help.place").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.omnivault.backpack.help.more").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
