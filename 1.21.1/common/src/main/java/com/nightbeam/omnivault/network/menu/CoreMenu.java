package com.nightbeam.omnivault.network.menu;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.NetworkPackets;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.block.NexusCoreBlockEntity;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Network overview: status, devices, storage use and energy. */
public class CoreMenu extends AbstractContainerMenu {
    @Nullable
    private final NexusCoreBlockEntity core;
    private final Player player;
    private int ticks;
    @Nullable
    private NetworkPackets.CoreStats stats;

    public CoreMenu(int containerId, Inventory inventory, @Nullable NexusCoreBlockEntity core) {
        super(NetworkContent.CORE_MENU.get(), containerId);
        this.core = core;
        this.player = inventory.player;
    }

    public static CoreMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        data.readBlockPos();
        return new CoreMenu(containerId, inventory, null);
    }

    @Nullable
    public NetworkPackets.CoreStats stats() {
        return stats;
    }

    public void setStats(NetworkPackets.CoreStats stats) {
        this.stats = stats;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (core != null && player instanceof ServerPlayer serverPlayer && ticks++ % 10 == 0) {
            StorageNetwork network = core.network();
            if (network == null) {
                return;
            }
            Services.PLATFORM.sendToPlayer(serverPlayer, new NetworkPackets.CoreStats(containerId, network.state().ordinal(),
                    network.nodeCount(), network.rawStorage().typeCount(), network.rawStorage().totalCount(), network.capacity(),
                    core.getEnergyStored(), core.getMaxEnergyStored(), network.energyUsage(), Map.copyOf(network.deviceCounts())));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return core == null || (!core.isRemoved() && player.distanceToSqr(core.getBlockPos().getCenter()) <= 64);
    }
}
