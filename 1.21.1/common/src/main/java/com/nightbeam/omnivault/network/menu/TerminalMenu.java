package com.nightbeam.omnivault.network.menu;

import com.nightbeam.omnivault.menu.OVMenu;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.NetworkPackets;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.CraftingHooks;
import com.nightbeam.omnivault.platform.Services;
import com.nightbeam.omnivault.storage.FluidKey;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.storage.ItemStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A view of the whole network. The grid is virtual: the server streams item counts to the client
 * and the client sends clicks back, so a terminal shows any number of item types without slots.
 */
public class TerminalMenu extends OVMenu {
    public static final int ACTION_TAKE_STACK = 0;
    public static final int ACTION_TAKE_HALF = 1;
    public static final int ACTION_TAKE_ONE = 2;
    public static final int ACTION_QUICK_MOVE = 3;
    public static final int ACTION_CRAFT = 4;

    public static final int COLUMNS = 9;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 20;
    private static final int SYNC_INTERVAL = 4;
    private static final int MAX_ENTRIES_PER_PACKET = 512;

    @Nullable
    protected final TerminalHost host;
    protected final Player player;
    private final int rows;

    // Server side.
    private final Map<ItemKey, Long> sentCounts = new HashMap<>();
    private Set<ItemKey> sentCraftables = Collections.emptySet();
    private long sentCounter = Long.MIN_VALUE;
    private boolean sentOnline;
    private boolean sentOnce;
    private int ticks;

    private final Map<FluidKey, Long> sentFluids = new HashMap<>();
    private long sentFluidCounter = Long.MIN_VALUE;
    private boolean sentFluidsOnce;

    // Client side.
    private final Map<FluidKey, Long> clientFluids = new LinkedHashMap<>();
    private final Map<ItemKey, ClientEntry> clientEntries = new LinkedHashMap<>();
    private boolean clientOnline;
    private int clientVersion;

    /** Server constructor. */
    public TerminalMenu(MenuType<?> type, int containerId, Inventory inventory, TerminalHost host) {
        this(type, containerId, inventory, host, 7);
        addPlayerInventory(inventory, 8, playerInventoryY());
    }

    /** Client constructor for the plain storage terminal. */
    public static TerminalMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        TerminalMenu menu = new TerminalMenu(NetworkContent.TERMINAL_MENU.get(), containerId, inventory, null, 7);
        menu.addPlayerInventory(inventory, 8, menu.playerInventoryY());
        return menu;
    }

    /**
     * Subclass constructor. Subclasses add their own slots (crafting grid, pattern slots) and then
     * call {@link #addPlayerInventory} themselves, so the player inventory always comes last.
     */
    protected TerminalMenu(MenuType<?> type, int containerId, Inventory inventory, @Nullable TerminalHost host, int rows) {
        super(type, containerId);
        this.host = host;
        this.player = inventory.player;
        this.rows = rows;
    }

    public int rows() {
        return rows;
    }

    public int gridBottom() {
        return GRID_Y + rows * 18;
    }

    public int playerInventoryY() {
        return gridBottom() + 16;
    }

    @Nullable
    protected StorageNetwork network() {
        return host == null ? null : host.network();
    }

    protected ItemStorage storage() {
        StorageNetwork network = network();
        return network == null ? ItemStorageEmpty.INSTANCE : network.storage();
    }

    @Override
    public boolean stillValid(Player player) {
        return host == null || host.stillValid(player);
    }

    // -- server: sync ------------------------------------------------------------------------------

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer && (!sentOnce || ++ticks % SYNC_INTERVAL == 0)) {
            sync(serverPlayer);
            syncFluids(serverPlayer);
        }
    }

    private void sync(ServerPlayer serverPlayer) {
        StorageNetwork network = network();
        boolean online = network != null && network.isActive();
        Set<ItemKey> craftables = online ? CraftingHooks.get().craftables(network) : Collections.emptySet();
        long counter = network == null ? -1 : network.changeCounter();
        if (sentOnce && online == sentOnline && counter == sentCounter && craftables.equals(sentCraftables)) {
            return;
        }
        Map<ItemKey, Long> now = new HashMap<>();
        if (online) {
            network.storage().forEach(now::put);
        }
        Set<ItemKey> touched = new HashSet<>(now.keySet());
        touched.addAll(sentCounts.keySet());
        touched.addAll(craftables);
        touched.addAll(sentCraftables);
        List<NetworkPackets.GridEntry> changes = new ArrayList<>();
        for (ItemKey key : touched) {
            long count = now.getOrDefault(key, 0L);
            boolean craftable = craftables.contains(key);
            if (sentCounts.getOrDefault(key, 0L) != count || sentCraftables.contains(key) != craftable || !sentOnce) {
                if (count > 0 || craftable || sentCounts.containsKey(key) || sentCraftables.contains(key)) {
                    changes.add(new NetworkPackets.GridEntry(key, count, craftable));
                }
            }
        }
        boolean reset = !sentOnce;
        sentCounts.clear();
        sentCounts.putAll(now);
        sentCraftables = Set.copyOf(craftables);
        sentCounter = counter;
        sentOnline = online;
        sentOnce = true;
        if (changes.isEmpty() && !reset) {
            // Only the online flag may have changed.
            Services.PLATFORM.sendToPlayer(serverPlayer, new NetworkPackets.GridContents(containerId, false, online, List.of()));
            return;
        }
        for (int from = 0; from < Math.max(1, changes.size()); from += MAX_ENTRIES_PER_PACKET) {
            List<NetworkPackets.GridEntry> part = changes.subList(from, Math.min(changes.size(), from + MAX_ENTRIES_PER_PACKET));
            Services.PLATFORM.sendToPlayer(serverPlayer, new NetworkPackets.GridContents(containerId, reset && from == 0, online, List.copyOf(part)));
        }
    }

    private void syncFluids(ServerPlayer serverPlayer) {
        StorageNetwork network = network();
        long counter = network == null ? -1 : network.fluidChangeCounter();
        if (sentFluidsOnce && counter == sentFluidCounter) {
            return;
        }
        Map<FluidKey, Long> now = new HashMap<>();
        if (network != null) {
            network.fluidStorage().forEach(now::put);
        }
        Map<FluidKey, Long> changes = new HashMap<>();
        now.forEach((key, amount) -> {
            if (!amount.equals(sentFluids.get(key))) {
                changes.put(key, amount);
            }
        });
        sentFluids.keySet().forEach(key -> {
            if (!now.containsKey(key)) {
                changes.put(key, 0L);
            }
        });
        boolean reset = !sentFluidsOnce;
        sentFluidCounter = counter;
        sentFluidsOnce = true;
        sentFluids.clear();
        sentFluids.putAll(now);
        if (reset || !changes.isEmpty()) {
            Services.PLATFORM.sendToPlayer(serverPlayer, new NetworkPackets.FluidContents(containerId, reset, changes));
        }
    }

    /**
     * Fluid view click: with {@code fill} set, fills one carried empty bucket with that fluid;
     * without it, empties the carried filled bucket into the network.
     */
    public void handleFluidClick(ServerPlayer player, @Nullable FluidKey fill) {
        StorageNetwork network = network();
        ItemStack carried = getCarried();
        if (network == null || !network.isActive() || !stillValid(player) || carried.isEmpty()) {
            return;
        }
        ItemStack result;
        if (fill != null) {
            if (!carried.is(Items.BUCKET) || fill.fluid().getBucket() == Items.AIR
                    || network.fluidStorage().extract(fill, FluidKey.BUCKET, true) < FluidKey.BUCKET) {
                return;
            }
            network.fluidStorage().extract(fill, FluidKey.BUCKET, false);
            result = new ItemStack(fill.fluid().getBucket());
        } else {
            FluidKey held = FluidKey.fromBucket(carried.getItem());
            if (held == null || network.fluidStorage().insert(held, FluidKey.BUCKET, true) < FluidKey.BUCKET) {
                return;
            }
            network.fluidStorage().insert(held, FluidKey.BUCKET, false);
            result = new ItemStack(Items.BUCKET);
        }
        carried.shrink(1);
        if (carried.isEmpty()) {
            setCarried(result);
        } else {
            setCarried(carried);
            player.getInventory().placeItemBackInInventory(result);
        }
    }

    // -- server: interaction -----------------------------------------------------------------------

    public void handleClick(ServerPlayer player, ItemKey key, int action) {
        StorageNetwork network = network();
        if (network == null || !network.isActive() || !stillValid(player)) {
            return;
        }
        ItemStorage storage = network.storage();
        ItemStack carried = getCarried();
        switch (action) {
            case ACTION_TAKE_STACK, ACTION_TAKE_HALF, ACTION_TAKE_ONE -> {
                if (!carried.isEmpty() && !key.matches(carried)) {
                    return;
                }
                int room = key.maxStackSize() - carried.getCount();
                long wanted = switch (action) {
                    case ACTION_TAKE_HALF -> (Math.min(storage.getCount(key), key.maxStackSize()) + 1) / 2;
                    case ACTION_TAKE_ONE -> 1;
                    default -> key.maxStackSize();
                };
                long taken = storage.extract(key, Math.min(room, wanted), false);
                if (taken > 0) {
                    setCarried(carried.isEmpty() ? key.toStack((int) taken) : carried.copyWithCount(carried.getCount() + (int) taken));
                }
            }
            case ACTION_QUICK_MOVE -> {
                long taken = storage.extract(key, key.maxStackSize(), false);
                if (taken > 0) {
                    ItemStack stack = key.toStack((int) taken);
                    player.getInventory().add(stack);
                    if (!stack.isEmpty()) {
                        storage.insert(key, stack.getCount(), false);
                    }
                }
            }
            case ACTION_CRAFT -> CraftingHooks.get().requestCraft(player, network, key, containerId);
            default -> {
            }
        }
    }

    public void handleInsertCarried(ServerPlayer player, boolean single) {
        StorageNetwork network = network();
        ItemStack carried = getCarried();
        ItemKey key = ItemKey.of(carried);
        if (network == null || key == null || !stillValid(player)) {
            return;
        }
        long inserted = network.storage().insert(key, single ? 1 : carried.getCount(), false);
        if (inserted > 0) {
            carried.shrink((int) inserted);
            setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        }
    }

    /** Shift-clicking a player slot sends the stack into the network. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= playerStart && index < playerEnd()) {
            Slot slot = slots.get(index);
            ItemStack stack = slot.getItem();
            ItemKey key = ItemKey.of(stack);
            if (key != null && !player.level().isClientSide) {
                long inserted = storage().insert(key, stack.getCount(), false);
                if (inserted > 0) {
                    stack.shrink((int) inserted);
                    slot.setChanged();
                }
            }
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    // -- client ------------------------------------------------------------------------------------

    public void applyContents(NetworkPackets.GridContents payload) {
        if (payload.reset()) {
            clientEntries.clear();
        }
        clientOnline = payload.online();
        for (NetworkPackets.GridEntry entry : payload.entries()) {
            if (entry.count() <= 0 && !entry.craftable()) {
                clientEntries.remove(entry.key());
            } else {
                clientEntries.put(entry.key(), new ClientEntry(entry.key(), entry.count(), entry.craftable()));
            }
        }
        clientVersion++;
    }

    public void applyFluids(NetworkPackets.FluidContents payload) {
        if (payload.reset()) {
            clientFluids.clear();
        }
        payload.fluids().forEach((key, amount) -> {
            if (amount <= 0) {
                clientFluids.remove(key);
            } else {
                clientFluids.put(key, amount);
            }
        });
        clientVersion++;
    }

    public Map<FluidKey, Long> clientFluids() {
        return clientFluids;
    }

    public Map<ItemKey, ClientEntry> clientEntries() {
        return clientEntries;
    }

    public boolean clientOnline() {
        return clientOnline;
    }

    /** Increases whenever the client view changes, so screens know when to re-sort. */
    public int clientVersion() {
        return clientVersion;
    }

    public record ClientEntry(ItemKey key, long count, boolean craftable) {
    }

    private enum ItemStorageEmpty implements ItemStorage {
        INSTANCE;

        @Override
        public long insert(ItemKey key, long amount, boolean simulate) {
            return 0;
        }

        @Override
        public long extract(ItemKey key, long amount, boolean simulate) {
            return 0;
        }

        @Override
        public void forEach(java.util.function.ObjLongConsumer<ItemKey> consumer) {
        }
    }
}
