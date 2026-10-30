package com.nightbeam.omnivault.autocraft.menu;

import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.AutocraftPackets;
import com.nightbeam.omnivault.autocraft.CraftingManager;
import com.nightbeam.omnivault.autocraft.plan.PlanError;
import com.nightbeam.omnivault.network.NetworkPackets;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.menu.TerminalHost;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.util.Formatting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * "How many?": the player picks an amount, asks for a plan (what is used, crafted and missing) and
 * starts the job, then goes back to the terminal they came from.
 */
public class CraftRequestMenu extends AbstractContainerMenu implements NetworkPackets.ValueReceiver {
    public static final int VALUE_CALCULATE = 0;
    public static final int VALUE_START = 1;
    public static final int BUTTON_BACK = 0;

    @Nullable
    private final TerminalHost host;
    private final ItemKey key;
    @Nullable
    private final Runnable back;
    private final boolean canGoBack;
    private final Player player;
    // Client side.
    @Nullable
    private AutocraftPackets.PlanView plan;
    private int planVersion;

    public static void open(ServerPlayer player, TerminalHost host, ItemKey key, @Nullable Runnable back) {
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider((id, inventory, p) -> new CraftRequestMenu(id, inventory, host, key, back),
                Component.translatable("gui.omnivault.craft.title")), buf -> {
            ItemKey.STREAM_CODEC.encode(buf, key);
            buf.writeBoolean(back != null);
        });
    }

    public static CraftRequestMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        ItemKey key = ItemKey.STREAM_CODEC.decode(data);
        boolean canGoBack = data.readBoolean();
        return new CraftRequestMenu(containerId, inventory, null, key, null, canGoBack);
    }

    private CraftRequestMenu(int containerId, Inventory inventory, @Nullable TerminalHost host, ItemKey key, @Nullable Runnable back) {
        this(containerId, inventory, host, key, back, back != null);
    }

    private CraftRequestMenu(int containerId, Inventory inventory, @Nullable TerminalHost host, ItemKey key, @Nullable Runnable back, boolean canGoBack) {
        super(AutocraftContent.CRAFT_REQUEST_MENU.get(), containerId);
        this.host = host;
        this.key = key;
        this.back = back;
        this.canGoBack = canGoBack;
        this.player = inventory.player;
    }

    public ItemKey key() {
        return key;
    }

    public boolean canGoBack() {
        return canGoBack;
    }

    @Nullable
    private CraftingManager manager() {
        StorageNetwork network = host == null ? null : host.network();
        return network == null ? null : CraftingManager.of(network);
    }

    @Override
    public void receiveValue(int id, long value) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CraftingManager manager = manager();
        if (manager == null) {
            send(serverPlayer, new AutocraftPackets.PlanView(value, PlanError.OFFLINE.ordinal(), 0, 0, false, 0, 0, java.util.List.of()));
            return;
        }
        if (id == VALUE_CALCULATE) {
            send(serverPlayer, AutocraftPackets.PlanView.of(manager.plan(key, value), manager));
        } else if (id == VALUE_START) {
            CraftingManager.StartResult result = manager.start(key, value, serverPlayer.getUUID());
            if (!result.started()) {
                send(serverPlayer, AutocraftPackets.PlanView.of(result.plan(), manager));
                return;
            }
            serverPlayer.displayClientMessage(Component.translatable("message.omnivault.craft.started", Formatting.full(value),
                    key.view().getHoverName()).withStyle(ChatFormatting.AQUA), true);
            if (back != null) {
                back.run();
            } else {
                serverPlayer.closeContainer();
            }
        }
    }

    private void send(ServerPlayer player, AutocraftPackets.PlanView view) {
        Services.PLATFORM.sendToPlayer(player, new AutocraftPackets.PlanResult(containerId, view));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_BACK && back != null) {
            back.run();
            return true;
        }
        return false;
    }

    // -- client ------------------------------------------------------------------------------------

    public void setPlan(AutocraftPackets.PlanView plan) {
        this.plan = plan;
        planVersion++;
    }

    @Nullable
    public AutocraftPackets.PlanView plan() {
        return plan;
    }

    public int planVersion() {
        return planVersion;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return host == null || host.stillValid(player);
    }
}
