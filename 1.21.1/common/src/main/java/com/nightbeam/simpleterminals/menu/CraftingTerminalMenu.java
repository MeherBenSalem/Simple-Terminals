package com.nightbeam.simpleterminals.menu;

import com.nightbeam.simpleterminals.registry.ModMenus;
import com.nightbeam.simpleterminals.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CraftingTerminalMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9;
    public static final int VISIBLE_ROWS = 6;
    public static final int VISIBLE_SLOT_COUNT = COLUMNS * VISIBLE_ROWS;
    public static final int STORAGE_SLOT_START = 0;
    public static final int CRAFTING_SLOT_START = STORAGE_SLOT_START + VISIBLE_SLOT_COUNT;
    public static final int CRAFTING_SLOT_COUNT = 9;
    public static final int RESULT_SLOT = CRAFTING_SLOT_START + CRAFTING_SLOT_COUNT;
    public static final int PLAYER_INVENTORY_START = RESULT_SLOT + 1;
    public static final int PLAYER_INVENTORY_COUNT = 36;
    public static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + PLAYER_INVENTORY_COUNT;

    private final Container target;
    private final int targetSlotCount;
    private final Player player;
    private final CraftingContainer craftingGrid = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer craftingResult = new ResultContainer();
    private final DataSlot offsetRows = DataSlot.standalone();
    private final List<Integer> filteredSlots = new ArrayList<>();
    private String searchQuery = "";

    public CraftingTerminalMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, new SimpleContainer(buffer.readVarInt()));
    }

    public CraftingTerminalMenu(int containerId, Inventory playerInventory, Container target) {
        super(ModMenus.craftingTerminalMenu(), containerId);
        this.target = target;
        this.targetSlotCount = target.getContainerSize();
        this.player = playerInventory.player;
        rebuildFilteredSlots();

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new ScrollingSlot(row * COLUMNS + column, 8 + column * 18, 34 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new CraftingSlot(craftingGrid, column + row * 3, 202 + column * 18, 52 + row * 18));
            }
        }
        addSlot(new TerminalResultSlot(playerInventory.player, craftingGrid, craftingResult, 0, 274, 70));

        int playerInventoryY = 158;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, playerInventoryY + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, playerInventoryY + 58));
        }

        addDataSlot(offsetRows);
    }

    public static CraftingTerminalMenu createServer(int containerId, Inventory playerInventory, BlockPos targetPos, Direction accessSide) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(targetPos);
        if (blockEntity instanceof Container container) {
            return new CraftingTerminalMenu(containerId, playerInventory, container);
        }
        Container platformContainer = Services.PLATFORM.findPlatformContainer(playerInventory.player.level(), targetPos, accessSide).orElse(null);
        if (platformContainer != null) {
            return new CraftingTerminalMenu(containerId, playerInventory, platformContainer);
        }
        return new CraftingTerminalMenu(containerId, playerInventory, new SimpleContainer(0));
    }

    public int getTargetSlotCount() {
        return targetSlotCount;
    }

    public int getMaxOffsetRows() {
        return Math.max(0, ((filteredSlots.size() + COLUMNS - 1) / COLUMNS) - VISIBLE_ROWS);
    }

    public int getOffsetRows() {
        return offsetRows.get();
    }

    public void setScrollOffsetRows(int rows) {
        offsetRows.set(Math.max(0, Math.min(rows, getMaxOffsetRows())));
        broadcastChanges();
    }

    public void setSearchQuery(String query) {
        searchQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        rebuildFilteredSlots();
        setScrollOffsetRows(Math.min(getOffsetRows(), getMaxOffsetRows()));
        broadcastChanges();
    }

    public static void handleScroll(Player player, int containerId, int offsetRows) {
        if (player.containerMenu instanceof CraftingTerminalMenu menu && menu.containerId == containerId) {
            menu.setScrollOffsetRows(offsetRows);
        }
    }

    public static void handleSearch(Player player, int containerId, String query) {
        if (player.containerMenu instanceof CraftingTerminalMenu menu && menu.containerId == containerId) {
            menu.setSearchQuery(query);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return target.stillValid(player);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == craftingGrid) {
            updateCraftingResult();
        }
        if (container == target && !searchQuery.isEmpty()) {
            rebuildFilteredSlots();
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            clearContainer(player, craftingGrid);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (slotIndex == RESULT_SLOT) {
                stack.getItem().onCraftedBy(stack, player.level(), player);
                if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)
                        && !moveItemStackTo(stack, STORAGE_SLOT_START, STORAGE_SLOT_START + VISIBLE_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, result);
            } else if (slotIndex >= STORAGE_SLOT_START && slotIndex < STORAGE_SLOT_START + VISIBLE_SLOT_COUNT) {
                if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= CRAFTING_SLOT_START && slotIndex < RESULT_SLOT) {
                if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)
                        && !moveItemStackTo(stack, STORAGE_SLOT_START, STORAGE_SLOT_START + VISIBLE_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= PLAYER_INVENTORY_START && slotIndex < PLAYER_INVENTORY_END) {
                if (!moveItemStackTo(stack, CRAFTING_SLOT_START, RESULT_SLOT, false)
                        && !moveItemStackTo(stack, STORAGE_SLOT_START, STORAGE_SLOT_START + VISIBLE_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (slotIndex == RESULT_SLOT) {
                slot.onTake(player, stack);
            }
        }
        return result;
    }

    private int backingSlot(int visibleSlot) {
        int filteredIndex = getOffsetRows() * COLUMNS + visibleSlot;
        return filteredIndex >= 0 && filteredIndex < filteredSlots.size() ? filteredSlots.get(filteredIndex) : -1;
    }

    private void rebuildFilteredSlots() {
        filteredSlots.clear();
        for (int slot = 0; slot < targetSlotCount; slot++) {
            ItemStack stack = target.getItem(slot);
            if (searchQuery.isEmpty() || (!stack.isEmpty() && stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(searchQuery))) {
                filteredSlots.add(slot);
            }
        }
    }

    private void updateCraftingResult() {
        if (player.level().isClientSide) {
            return;
        }
        CraftingInput input = craftingGrid.asCraftInput();
        ItemStack result = player.level().getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, player.level())
                .map(holder -> {
                    craftingResult.setRecipeUsed(holder);
                    return holder.value().assemble(input, player.level().registryAccess());
                })
                .orElse(ItemStack.EMPTY);
        craftingResult.setItem(0, result);
        setRemoteSlot(RESULT_SLOT, result);
        broadcastChanges();
    }

    private class ScrollingSlot extends Slot {
        private final int visibleSlot;

        ScrollingSlot(int visibleSlot, int x, int y) {
            super(target, 0, x, y);
            this.visibleSlot = visibleSlot;
        }

        private int backingSlot() {
            return CraftingTerminalMenu.this.backingSlot(visibleSlot);
        }

        @Override
        public boolean isActive() {
            return backingSlot() >= 0;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && target.canPlaceItem(backingSlot(), stack);
        }

        @Override
        public ItemStack getItem() {
            return isActive() ? target.getItem(backingSlot()) : ItemStack.EMPTY;
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public void set(ItemStack stack) {
            if (isActive()) {
                target.setItem(backingSlot(), stack);
                setChanged();
            }
        }

        @Override
        public void setChanged() {
            target.setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return target.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(getMaxStackSize(), stack.getMaxStackSize());
        }

        @Override
        public ItemStack remove(int amount) {
            return isActive() ? target.removeItem(backingSlot(), amount) : ItemStack.EMPTY;
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive() && target.stillValid(player);
        }
    }

    private class CraftingSlot extends Slot {
        CraftingSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            CraftingTerminalMenu.this.slotsChanged(container);
        }
    }

    private class TerminalResultSlot extends ResultSlot {
        TerminalResultSlot(Player player, CraftingContainer craftingContainer, Container resultContainer, int slot, int x, int y) {
            super(player, craftingContainer, resultContainer, slot, x, y);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            updateCraftingResult();
        }
    }
}
