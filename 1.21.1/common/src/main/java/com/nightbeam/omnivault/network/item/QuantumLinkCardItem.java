package com.nightbeam.omnivault.network.item;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.block.QuantumBridgeBlock;
import com.nightbeam.omnivault.network.block.QuantumBridgeBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Use on one Quantum Bridge to record it, then on another to entangle the pair (the card is used up).
 */
public class QuantumLinkCardItem extends Item {
    public QuantumLinkCardItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(NetworkContent.BRIDGE_LINK.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof QuantumBridgeBlockEntity bridge)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }
        ItemStack card = context.getItemInHand();
        GlobalPos first = card.get(NetworkContent.BRIDGE_LINK.get());
        GlobalPos here = bridge.self();
        if (first == null || first.equals(here)) {
            // Record this bridge on a single card split off the stack.
            ItemStack recorded = card.split(1);
            recorded.set(NetworkContent.BRIDGE_LINK.get(), here);
            player.getInventory().placeItemBackInInventory(recorded);
            player.displayClientMessage(Component.translatable("message.omnivault.bridge.recorded").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return InteractionResult.CONSUME;
        }
        ServerLevel otherLevel = ((ServerLevel) level).getServer().getLevel(first.dimension());
        if (otherLevel == null || !otherLevel.isLoaded(first.pos())
                || !(otherLevel.getBlockEntity(first.pos()) instanceof QuantumBridgeBlockEntity other)) {
            player.displayClientMessage(Component.translatable("message.omnivault.bridge.first_missing").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        QuantumBridgeBlock.unlink(bridge);
        QuantumBridgeBlock.unlink(other);
        bridge.setPartner(first);
        other.setPartner(here);
        card.shrink(1);
        level.playSound(null, context.getClickedPos(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.8f, 1.6f);
        player.displayClientMessage(Component.translatable("message.omnivault.bridge.entangled").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos link = stack.get(NetworkContent.BRIDGE_LINK.get());
        if (link == null) {
            tooltip.add(Component.translatable("tooltip.omnivault.link_card.blank").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.omnivault.link_card.recorded", link.pos().getX(), link.pos().getY(), link.pos().getZ(),
                    link.dimension().location().toString()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
