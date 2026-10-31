package com.nightbeam.omnivault.backpack;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.packet.PacketCodec;
import com.nightbeam.omnivault.packet.PacketCodecs;
import com.nightbeam.omnivault.packet.PacketRegistrar;
import com.nightbeam.omnivault.packet.Payload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Client-to-server payloads of the backpack feature. */
public final class BackpackPayloads {
    private BackpackPayloads() {
    }

    /** The open-backpack key was pressed. */
    public record OpenBackpack() implements Payload {
        public static final OpenBackpack INSTANCE = new OpenBackpack();
        public static final Type<OpenBackpack> TYPE = new Type<>(OmniVault.id("open_backpack"));
        public static final PacketCodec<OpenBackpack> CODEC = PacketCodec.unit(INSTANCE);

        @Override
        public Type<? extends Payload> type() {
            return TYPE;
        }
    }

    /** A change made in an upgrade's tab: see the {@code ACTION_*} constants in {@link BackpackMenu}. */
    public record ConfigureUpgrade(int containerId, int upgradeSlot, int action, int value, ItemStack stack) implements Payload {
        public static final Type<ConfigureUpgrade> TYPE = new Type<>(OmniVault.id("configure_backpack_upgrade"));
        public static final PacketCodec<ConfigureUpgrade> CODEC = PacketCodec.composite(
                PacketCodecs.VAR_INT, ConfigureUpgrade::containerId,
                PacketCodecs.VAR_INT, ConfigureUpgrade::upgradeSlot,
                PacketCodecs.VAR_INT, ConfigureUpgrade::action,
                PacketCodecs.VAR_INT, ConfigureUpgrade::value,
                PacketCodecs.ITEM_STACK, ConfigureUpgrade::stack,
                ConfigureUpgrade::new);

        @Override
        public Type<? extends Payload> type() {
            return TYPE;
        }
    }

    static void register(PacketRegistrar registrar) {
        registrar.toServer(OpenBackpack.TYPE, OpenBackpack.CODEC, (payload, player) -> openFromKey(player));
        registrar.toServer(ConfigureUpgrade.TYPE, ConfigureUpgrade.CODEC, (payload, player) -> {
            if (player.containerMenu instanceof BackpackMenu menu && menu.containerId == payload.containerId() && menu.stillValid(player)) {
                menu.configureUpgrade(payload.upgradeSlot(), payload.action(), payload.value(), payload.stack());
                menu.broadcastChanges();
            }
        });
    }

    private static void openFromKey(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        int slot = Backpacks.slotForKey(player);
        if (slot < 0) {
            player.displayClientMessage(Component.translatable("message.omnivault.backpack.none"), true);
            return;
        }
        Backpacks.open(player, new BackpackSource.Held(slot));
    }
}
