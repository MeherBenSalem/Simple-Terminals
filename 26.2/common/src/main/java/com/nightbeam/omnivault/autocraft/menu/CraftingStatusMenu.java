package com.nightbeam.omnivault.autocraft.menu;

import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.AutocraftPackets;
import com.nightbeam.omnivault.autocraft.CraftingManager;
import com.nightbeam.omnivault.autocraft.block.CraftingProcessorBlockEntity;
import com.nightbeam.omnivault.autocraft.job.CraftingJob;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.platform.Services;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Every crafting job on a network with its progress; jobs can be cancelled from here. */
public class CraftingStatusMenu extends AbstractContainerMenu {
    public static final int BUTTON_BACK = 0;
    private static final int SYNC_INTERVAL = 10;

    @Nullable
    private final Supplier<StorageNetwork> network;
    @Nullable
    private final Predicate<Player> valid;
    @Nullable
    private final Runnable back;
    private final boolean canGoBack;
    private final Player player;
    private int ticks;
    // Client side.
    private List<AutocraftPackets.JobView> jobs = List.of();
    private int processors;
    private boolean received;

    public static void open(ServerPlayer player, Supplier<StorageNetwork> network, Predicate<Player> valid, @Nullable Runnable back) {
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider((id, inventory, p) -> new CraftingStatusMenu(id, inventory, network, valid, back),
                Component.translatable("gui.omnivault.status.title")), buf -> buf.writeBoolean(back != null));
    }

    public static CraftingStatusMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        return new CraftingStatusMenu(containerId, inventory, null, null, null, data.readBoolean());
    }

    private CraftingStatusMenu(int containerId, Inventory inventory, Supplier<StorageNetwork> network, Predicate<Player> valid, @Nullable Runnable back) {
        this(containerId, inventory, network, valid, back, back != null);
    }

    private CraftingStatusMenu(int containerId, Inventory inventory, @Nullable Supplier<StorageNetwork> network, @Nullable Predicate<Player> valid,
                               @Nullable Runnable back, boolean canGoBack) {
        super(AutocraftContent.CRAFTING_STATUS_MENU.get(), containerId);
        this.network = network;
        this.valid = valid;
        this.back = back;
        this.canGoBack = canGoBack;
        this.player = inventory.player;
    }

    @Nullable
    private CraftingManager manager() {
        StorageNetwork current = network == null ? null : network.get();
        return current == null ? null : CraftingManager.of(current);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer && ticks++ % SYNC_INTERVAL == 0) {
            CraftingManager manager = manager();
            List<AutocraftPackets.JobView> views = new ArrayList<>();
            int count = 0;
            if (manager != null) {
                count = manager.processors().size();
                for (CraftingProcessorBlockEntity processor : manager.busyProcessors()) {
                    CraftingJob job = processor.job();
                    if (job != null) {
                        views.add(AutocraftPackets.JobView.of(job));
                    }
                }
            }
            Services.PLATFORM.sendToPlayer(serverPlayer, new AutocraftPackets.JobList(containerId, count, views));
        }
    }

    public void cancel(UUID job) {
        CraftingManager manager = manager();
        if (manager != null) {
            manager.cancel(job);
            ticks = 0;
        }
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

    public void setJobs(AutocraftPackets.JobList list) {
        this.jobs = list.jobs();
        this.processors = list.processors();
        this.received = true;
    }

    public List<AutocraftPackets.JobView> jobs() {
        return jobs;
    }

    public int processorCount() {
        return processors;
    }

    public boolean received() {
        return received;
    }

    public boolean canGoBack() {
        return canGoBack;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return valid == null || valid.test(player);
    }
}
