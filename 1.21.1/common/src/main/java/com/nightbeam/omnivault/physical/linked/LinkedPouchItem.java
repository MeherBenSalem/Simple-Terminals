package com.nightbeam.omnivault.physical.linked;

import com.nightbeam.omnivault.physical.PhysicalConfig;
import com.nightbeam.omnivault.physical.PhysicalContent;
import com.nightbeam.omnivault.physical.StorageMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Opens a Linked Vault channel from anywhere. Sneak-right-click a Linked Vault to bind the pouch to its
 * frequency (private channels can only be bound and opened by their owner).
 */
public class LinkedPouchItem extends Item {
    public LinkedPouchItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static LinkedFrequency frequency(ItemStack stack) {
        return stack.get(PhysicalContent.LINKED_FREQUENCY.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !player.isSecondaryUseActive()
                || !(level.getBlockEntity(context.getClickedPos()) instanceof LinkedVaultBlockEntity vault)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (!vault.canUse(player)) {
                player.displayClientMessage(Component.translatable("message.omnivault.linked.not_owner", vault.frequency().ownerName())
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            context.getItemInHand().set(PhysicalContent.LINKED_FREQUENCY.get(), vault.frequency());
            player.displayClientMessage(Component.translatable("message.omnivault.linked_pouch.bound", vault.frequency().describe()), true);
            level.playSound(null, context.getClickedPos(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        LinkedFrequency frequency = frequency(stack);
        if (frequency == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.omnivault.linked_pouch.unbound").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (frequency.isPrivate() && (!PhysicalConfig.LINKED_PRIVATE.get() || !frequency.isOwner(player.getUUID()))) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.omnivault.linked.not_owner", frequency.ownerName())
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            LinkedAccess.open(serverPlayer, frequency, stack.getHoverName(), new PouchSource(frequency));
            level.playSound(null, player.blockPosition(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.4f, 1.3f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LinkedFrequency frequency = frequency(stack);
        if (frequency == null) {
            tooltip.add(Component.translatable("item.omnivault.linked_pouch.unbound").withStyle(ChatFormatting.GRAY));
        } else {
            frequency.appendTooltip(tooltip);
        }
        tooltip.add(Component.translatable("item.omnivault.linked_pouch.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The pouch menu stays open while the player still holds a pouch on the same channel. */
    private record PouchSource(LinkedFrequency frequency) implements StorageMenu.Source {
        @Override
        public boolean stillValid(Player player) {
            for (InteractionHand hand : InteractionHand.values()) {
                LinkedFrequency held = LinkedPouchItem.frequency(player.getItemInHand(hand));
                if (held != null && held.key().equals(frequency.key())) {
                    return true;
                }
            }
            return false;
        }
    }
}
