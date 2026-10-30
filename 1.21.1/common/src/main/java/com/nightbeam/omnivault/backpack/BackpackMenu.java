package com.nightbeam.omnivault.backpack;

import com.nightbeam.omnivault.backpack.upgrade.BackpackUpgradeItem;
import com.nightbeam.omnivault.backpack.upgrade.UpgradeConfig;
import com.nightbeam.omnivault.backpack.upgrade.UpgradeType;
import com.nightbeam.omnivault.menu.OVMenu;
import com.nightbeam.omnivault.util.InventorySorting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The backpack screen's menu. Slot order: visible backpack slots, upgrade slots, crafting result,
 * crafting grid, then the player inventory.
 *
 * <p>Big backpacks scroll. Vanilla slots cannot move, so the visible slots are <em>virtual</em>: on the
 * server each one shows backing slot {@code index + scrollRow * columns}; changing the row re-sends every
 * slot. The client only ever holds the visible slots (its container is indexed by visible slot), so a
 * scroll can never mix up which item belongs where.
 */
public class BackpackMenu extends OVMenu implements InventorySorting.Sortable {
    /** {@code clickMenuButton} ids at or above this scroll to row {@code id - SCROLL_BUTTON}. */
    public static final int SCROLL_BUTTON = 1000;

    public static final int ACTION_FILTER = 0;
    public static final int ACTION_ALLOW = 1;
    public static final int ACTION_COMPONENTS = 2;
    public static final int ACTION_RANGE = 3;
    public static final int ACTION_MODE = 4;
    public static final int ACTION_ENABLED = 5;

    private final Player player;
    private final BackpackTier tier;
    private final BackpackLayout layout;
    private final BackpackSource source;
    /** Server: the backpack contents. Client: the visible slots only. */
    private final Container items;
    private final Container upgrades;
    @Nullable
    private final BackpackData data;
    @Nullable
    private final UUID id;
    private final DataSlot scrollRow = DataSlot.standalone();
    private final DataSlot usedSlots = DataSlot.standalone();
    private final TransientCraftingContainer craftGrid = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer craftResult = new ResultContainer();
    private final int upgradeStart;
    private final int craftResultIndex;
    private final int craftGridStart;
    private final int lockedMenuSlot;
    /** Client: whether the crafting tab is expanded (crafting slots are only active then). */
    public boolean craftingTabOpen;

    /** Server side. */
    public BackpackMenu(int containerId, Inventory inventory, BackpackData data, BackpackTier tier, BackpackSource source, UUID id) {
        this(containerId, inventory, tier, data.items().getContainerSize(), data.upgrades().getContainerSize(), source,
                data.items(), data.upgrades(), data, id);
    }

    private BackpackMenu(int containerId, Inventory inventory, BackpackTier tier, int totalSlots, int upgradeSlots, BackpackSource source,
                         Container items, Container upgrades, @Nullable BackpackData data, @Nullable UUID id) {
        super(BackpackContent.MENU.get(), containerId);
        this.player = inventory.player;
        this.tier = tier;
        this.layout = BackpackLayout.of(tier, totalSlots, upgradeSlots);
        this.source = source;
        this.items = items;
        this.upgrades = upgrades;
        this.data = data;
        this.id = id;

        for (int i = 0; i < layout.visibleSlots(); i++) {
            addSlot(new BackpackSlot(this, items, i, layout.gridX() + (i % layout.columns()) * BackpackLayout.SLOT,
                    layout.gridY() + (i / layout.columns()) * BackpackLayout.SLOT));
        }
        upgradeStart = slots.size();
        for (int i = 0; i < upgradeSlots; i++) {
            addSlot(new UpgradeSlot(this, upgrades, i, BackpackLayout.BAY_SLOT_X, BackpackLayout.BAY_SLOT_Y + i * BackpackLayout.SLOT));
        }
        int panelX = layout.panelX();
        craftResultIndex = slots.size();
        addSlot(new ResultSlot(player, craftGrid, craftResult, 0, panelX + BackpackLayout.CRAFT_RESULT_DX,
                BackpackLayout.PANEL_Y + BackpackLayout.CRAFT_RESULT_DY) {
            @Override
            public boolean isActive() {
                return craftingActive();
            }
        });
        craftGridStart = slots.size();
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(craftGrid, i, panelX + BackpackLayout.CRAFT_GRID_DX + (i % 3) * BackpackLayout.SLOT,
                    BackpackLayout.PANEL_Y + BackpackLayout.CRAFT_GRID_DY + (i / 3) * BackpackLayout.SLOT) {
                @Override
                public boolean isActive() {
                    return craftingActive();
                }
            });
        }
        addPlayerInventory(inventory, layout.inventoryX(), layout.inventoryY());
        addDataSlot(scrollRow);
        addDataSlot(usedSlots);
        if (data != null) {
            usedSlots.set(data.items().usedSlots());
        }

        int locked = -1;
        if (source instanceof BackpackSource.Held held && held.slot() >= 0 && held.slot() < Inventory.INVENTORY_SIZE) {
            locked = playerStart + (held.slot() < 9 ? 27 + held.slot() : held.slot() - 9);
        }
        lockedMenuSlot = locked;
    }

    /** Client side, from the data written by {@link #writeOpenData}. */
    public static BackpackMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BackpackTier tier = BackpackTier.byOrdinal(buf.readVarInt());
        int totalSlots = buf.readVarInt();
        int upgradeSlots = buf.readVarInt();
        BackpackSource source = BackpackSource.read(buf);
        BackpackLayout layout = BackpackLayout.of(tier, totalSlots, upgradeSlots);
        return new BackpackMenu(containerId, inventory, tier, totalSlots, upgradeSlots, source,
                new SimpleContainer(layout.visibleSlots()), new SimpleContainer(upgradeSlots), null, null);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buf, BackpackTier tier, BackpackData data, BackpackSource source) {
        buf.writeVarInt(tier.ordinal());
        buf.writeVarInt(data.items().getContainerSize());
        buf.writeVarInt(data.upgrades().getContainerSize());
        BackpackSource.write(buf, source);
    }

    // ---------------------------------------------------------------------------------------------
    // Accessors for the screen
    // ---------------------------------------------------------------------------------------------

    public BackpackTier tier() {
        return tier;
    }

    public BackpackLayout layout() {
        return layout;
    }

    public BackpackSource source() {
        return source;
    }

    public int scrollRow() {
        return Math.max(0, Math.min(layout.maxScroll(), scrollRow.get()));
    }

    public int usedSlots() {
        return usedSlots.get();
    }

    public int upgradeStart() {
        return upgradeStart;
    }

    public int craftResultIndex() {
        return craftResultIndex;
    }

    public int craftGridStart() {
        return craftGridStart;
    }

    /** Menu index of the player slot holding this (open) backpack, which cannot be moved; -1 if none. */
    public int lockedMenuSlot() {
        return lockedMenuSlot;
    }

    public Container upgradeContainer() {
        return upgrades;
    }

    private boolean isServer() {
        return data != null;
    }

    /** Installed and enabled (checked on either side; the client sees the synced upgrade slots). */
    public boolean hasUpgrade(UpgradeType type) {
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            ItemStack stack = upgrades.getItem(i);
            if (stack.getItem() instanceof BackpackUpgradeItem item && item.type() == type && BackpackUpgradeItem.config(stack).enabled()) {
                return true;
            }
        }
        return false;
    }

    private boolean craftingActive() {
        return hasUpgrade(UpgradeType.CRAFTING) && (isServer() || craftingTabOpen);
    }

    // ---------------------------------------------------------------------------------------------
    // Virtual slot mapping
    // ---------------------------------------------------------------------------------------------

    /** The container index visible slot {@code visible} reads from, or -1 if it shows nothing. */
    int readIndex(int visible) {
        int backing = visible + scrollRow() * layout.columns();
        if (backing >= layout.totalSlots()) {
            return -1;
        }
        return isServer() ? (backing < items.getContainerSize() ? backing : -1) : visible;
    }

    /** The container index a write to visible slot {@code visible} goes to (clients always keep what they are sent). */
    int writeIndex(int visible) {
        if (!isServer()) {
            return visible;
        }
        return readIndex(visible);
    }

    // ---------------------------------------------------------------------------------------------
    // Interaction
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= SCROLL_BUTTON) {
            int row = Math.max(0, Math.min(layout.maxScroll(), id - SCROLL_BUTTON));
            if (isServer() && row != scrollRow.get()) {
                scrollRow.set(row);
                // Every visible slot now shows a different backing slot: resend them all.
                sendAllDataToRemote();
            }
            return true;
        }
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (lockedMenuSlot >= 0 && slotId == lockedMenuSlot) {
            return;
        }
        if (clickType == ClickType.SWAP && source instanceof BackpackSource.Held held && button == held.slot()) {
            // Number keys / F would move the open backpack out of its slot.
            return;
        }
        if (slotId >= craftResultIndex && slotId < craftGridStart + 9 && !hasUpgrade(UpgradeType.CRAFTING)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || index == lockedMenuSlot) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == craftResultIndex) {
            stack.getItem().onCraftedBy(stack, player.level(), player);
            if (!moveItemStackTo(stack, playerStart, playerEnd(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, playerEnd(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = stack.getItem() instanceof BackpackUpgradeItem && moveItemStackTo(stack, upgradeStart, upgradeStart + upgrades.getContainerSize(), false);
            if (!moved || !stack.isEmpty()) {
                moved |= insertIntoBackpack(stack);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        if (index == craftResultIndex) {
            player.drop(stack, false);
        }
        return original;
    }

    /** Shift-click into the backpack: the server fills the whole backpack, not just the visible rows. */
    private boolean insertIntoBackpack(ItemStack stack) {
        if (!Backpacks.canStore(stack)) {
            return false;
        }
        if (data != null) {
            ItemStack rest = data.items().insert(stack, false);
            boolean moved = rest.getCount() != stack.getCount();
            stack.setCount(rest.getCount());
            return moved;
        }
        return moveItemStackTo(stack, 0, layout.visibleSlots(), false);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != craftResult && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void slotsChanged(Container container) {
        if (container == craftGrid && player instanceof ServerPlayer serverPlayer) {
            updateCraftingResult(serverPlayer);
        }
        super.slotsChanged(container);
    }

    private void updateCraftingResult(ServerPlayer serverPlayer) {
        CraftingInput input = craftGrid.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        if (hasUpgrade(UpgradeType.CRAFTING)) {
            Optional<RecipeHolder<CraftingRecipe>> recipe = serverPlayer.server.getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, serverPlayer.level());
            if (recipe.isPresent() && craftResult.setRecipeUsed(serverPlayer.level(), serverPlayer, recipe.get())) {
                ItemStack assembled = recipe.get().value().assemble(input, serverPlayer.level().registryAccess());
                if (assembled.isItemEnabled(serverPlayer.level().enabledFeatures())) {
                    result = assembled;
                }
            }
        }
        craftResult.setItem(0, result);
        setRemoteSlot(craftResultIndex, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), craftResultIndex, result));
    }

    @Override
    public void broadcastChanges() {
        if (data != null) {
            usedSlots.set(data.items().usedSlots());
            if (!craftGrid.isEmpty() && !hasUpgrade(UpgradeType.CRAFTING)) {
                // The crafting upgrade was taken out: hand the grid back.
                clearContainer(player, craftGrid);
                craftResult.setItem(0, ItemStack.EMPTY);
            }
        }
        super.broadcastChanges();
    }

    @Override
    public void sortSection(int section) {
        if (data != null) {
            InventorySorting.sort(data.items(), 0, data.items().getContainerSize());
        }
    }

    /** Applies a setting change from the upgrade tab (server side). */
    public void configureUpgrade(int upgradeSlot, int action, int value, ItemStack stack) {
        if (upgradeSlot < 0 || upgradeSlot >= upgrades.getContainerSize()) {
            return;
        }
        ItemStack card = upgrades.getItem(upgradeSlot);
        if (!(card.getItem() instanceof BackpackUpgradeItem item)) {
            return;
        }
        UpgradeType type = item.type();
        UpgradeConfig config = BackpackUpgradeItem.config(card);
        UpgradeConfig updated = switch (action) {
            case ACTION_FILTER -> type.hasFilter() ? config.withFilter(value, stack) : config;
            case ACTION_ALLOW -> config.withAllow(value != 0);
            case ACTION_COMPONENTS -> config.withMatchComponents(value != 0);
            case ACTION_RANGE -> type.hasRange() ? config.withRange(Math.max(1, Math.min(BackpackConfig.MAGNET_MAX_RANGE.get(), value))) : config;
            case ACTION_MODE -> type.modes() > 0 ? config.withMode(Math.max(0, Math.min(type.modes() - 1, value))) : config;
            case ACTION_ENABLED -> config.withEnabled(value != 0);
            default -> config;
        };
        if (!updated.equals(config)) {
            ItemStack copy = card.copy();
            BackpackUpgradeItem.setConfig(copy, updated);
            upgrades.setItem(upgradeSlot, copy);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (data == null || id == null) {
            return true;
        }
        ItemStack stack = source.stack(player);
        if (!Backpacks.isBackpack(stack) || !id.equals(Backpacks.getId(stack))) {
            return false;
        }
        if (source instanceof BackpackSource.Placed placed) {
            return player.distanceToSqr(Vec3.atCenterOf(placed.pos())) <= 64;
        }
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, craftGrid);
        if (data != null) {
            ItemStack stack = source.stack(player);
            if (Backpacks.isBackpack(stack) && id != null && id.equals(Backpacks.getId(stack))) {
                Backpacks.refreshSummary(stack, data);
                if (source instanceof BackpackSource.Placed placed && placed.blockEntity(player.level()) instanceof BackpackBlockEntity blockEntity) {
                    blockEntity.setChanged();
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Slots
    // ---------------------------------------------------------------------------------------------

    /** A visible backpack slot showing whichever backing slot the scroll position maps it to. */
    public static class BackpackSlot extends Slot {
        private final BackpackMenu menu;
        private final int visible;

        BackpackSlot(BackpackMenu menu, Container container, int visible, int x, int y) {
            super(container, visible, x, y);
            this.menu = menu;
            this.visible = visible;
        }

        public boolean exists() {
            return menu.readIndex(visible) >= 0;
        }

        @Override
        public ItemStack getItem() {
            int index = menu.readIndex(visible);
            return index < 0 ? ItemStack.EMPTY : container.getItem(index);
        }

        @Override
        public void set(ItemStack stack) {
            int index = menu.writeIndex(visible);
            if (index >= 0) {
                container.setItem(index, stack);
                setChanged();
            }
        }

        @Override
        public ItemStack remove(int amount) {
            int index = menu.readIndex(visible);
            return index < 0 ? ItemStack.EMPTY : container.removeItem(index, amount);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return exists() && Backpacks.canStore(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return exists();
        }

        @Override
        public boolean isActive() {
            return exists();
        }

        @Override
        public int getContainerSlot() {
            int index = menu.readIndex(visible);
            return index < 0 ? visible : index;
        }
    }

    /** One upgrade card per type; cards stack to one. */
    public static class UpgradeSlot extends Slot {
        private final BackpackMenu menu;

        UpgradeSlot(BackpackMenu menu, Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof BackpackUpgradeItem item)) {
                return false;
            }
            for (int i = 0; i < menu.upgrades.getContainerSize(); i++) {
                if (i != getContainerSlot() && menu.upgrades.getItem(i).getItem() instanceof BackpackUpgradeItem other && other.type() == item.type()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
