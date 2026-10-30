package com.nightbeam.omnivault.network.item;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import com.nightbeam.omnivault.network.menu.CraftingTerminalMenu;
import com.nightbeam.omnivault.network.menu.TerminalHost;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A handheld crafting terminal. Sneak-use it on a Nexus Core to link it; it then works anywhere in
 * range of the core or a Wireless Transmitter. The quantum version works at any distance, in any
 * dimension, as long as the core's chunk is loaded.
 */
public class WirelessTerminalItem extends Item {
    private final boolean quantum;

    public WirelessTerminalItem(boolean quantum, Properties properties) {
        super(properties.stacksTo(1));
        this.quantum = quantum;
    }

    public boolean quantum() {
        return quantum;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return quantum || super.isFoil(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !player.isShiftKeyDown() || !(level.getBlockEntity(context.getClickedPos()) instanceof NexusCoreBlockEntity core)) {
            return InteractionResult.PASS;
        }
        if (core.isBasic()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.omnivault.wireless.needs_core").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            context.getItemInHand().set(NetworkContent.NETWORK_LINK.get(), GlobalPos.of(level.dimension(), context.getClickedPos()));
            player.displayClientMessage(Component.translatable("message.omnivault.wireless.linked").withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
            open(serverPlayer, slot);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Opens the terminal held in inventory slot {@code slot} (40 = off hand), if it can reach its network. */
    public static void open(ServerPlayer player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (!(stack.getItem() instanceof WirelessTerminalItem item)) {
            return;
        }
        GlobalPos link = stack.get(NetworkContent.NETWORK_LINK.get());
        if (link == null) {
            player.displayClientMessage(Component.translatable("message.omnivault.wireless.unlinked").withStyle(ChatFormatting.RED), true);
            return;
        }
        Host host = new Host(player, slot, link, item.quantum);
        StorageNetwork network = host.network();
        if (network == null) {
            player.displayClientMessage(Component.translatable("message.omnivault.wireless.no_network").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!host.inRange(player)) {
            player.displayClientMessage(Component.translatable("message.omnivault.wireless.out_of_range").withStyle(ChatFormatting.RED), true);
            return;
        }
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider((id, inventory, p) -> new CraftingTerminalMenu(id, inventory, host),
                stack.getHoverName()), buf -> {
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos link = stack.get(NetworkContent.NETWORK_LINK.get());
        if (link == null) {
            tooltip.add(Component.translatable("tooltip.omnivault.wireless.unlinked").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.omnivault.wireless.linked", link.pos().getX(), link.pos().getY(), link.pos().getZ(),
                    link.dimension().location().toString()).withStyle(ChatFormatting.GRAY));
        }
        if (quantum) {
            tooltip.add(Component.translatable("tooltip.omnivault.wireless.quantum").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** The terminal as a menu host: valid while the item stays in its slot and in range. */
    public record Host(ServerPlayer player, int slot, GlobalPos link, boolean quantum) implements TerminalHost {
        @Nullable
        @Override
        public StorageNetwork network() {
            ServerLevel level = player.server.getLevel(link.dimension());
            if (level == null || !level.isLoaded(link.pos())) {
                return null;
            }
            return level.getBlockEntity(link.pos()) instanceof NexusCoreBlockEntity core && !core.isBasic() ? core.network() : null;
        }

        boolean inRange(Player player) {
            if (quantum) {
                return true;
            }
            StorageNetwork network = network();
            return network != null && network.inWirelessRange(player);
        }

        @Override
        public boolean stillValid(Player player) {
            ItemStack stack = player.getInventory().getItem(slot);
            return stack.getItem() instanceof WirelessTerminalItem && link.equals(stack.get(NetworkContent.NETWORK_LINK.get())) && inRange(player);
        }
    }
}
