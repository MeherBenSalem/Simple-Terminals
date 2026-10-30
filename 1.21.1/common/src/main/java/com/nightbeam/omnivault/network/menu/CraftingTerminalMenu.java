package com.nightbeam.omnivault.network.menu;

import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * A terminal with a 3×3 crafting grid. After every craft the grid refills itself from the
 * network, so shift-clicking the result crafts as many as the network can supply.
 */
public class CraftingTerminalMenu extends TerminalMenu {
    public static final int CRAFT_X = 26;
    public static final int RESULT_X = 120;
    public static final int BUTTON_CLEAR = 0;

    private final CraftingContainer craftSlots;
    private final ResultContainer resultSlots = new ResultContainer();
    private final boolean persistent;
    private final int craftStart;
    private final int resultIndex;
    private boolean refilling;

    public static CraftingTerminalMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        return new CraftingTerminalMenu(containerId, inventory, null);
    }

    public CraftingTerminalMenu(int containerId, Inventory inventory, @Nullable TerminalHost host) {
        super(NetworkContent.CRAFTING_TERMINAL_MENU.get(), containerId, inventory, host, 4);
        NonNullList<ItemStack> grid = host == null ? null : host.persistentCraftingGrid();
        this.persistent = grid != null;
        this.craftSlots = new TransientCraftingContainer(this, 3, 3, grid != null ? grid : NonNullList.withSize(9, ItemStack.EMPTY));
        int top = craftTop();
        craftStart = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(craftSlots, column + row * 3, CRAFT_X + column * 18, top + row * 18));
            }
        }
        resultIndex = slots.size();
        addSlot(new TerminalResultSlot(inventory.player, craftSlots, resultSlots, RESULT_X, top + 18));
        addPlayerInventory(inventory, 8, playerInventoryY());
        if (!inventory.player.level().isClientSide) {
            updateResult();
        }
    }

    public int craftTop() {
        return gridBottom() + 10;
    }

    @Override
    public int playerInventoryY() {
        return craftTop() + 3 * 18 + 18;
    }

    public int resultIndex() {
        return resultIndex;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == craftSlots && !refilling) {
            updateResult();
            if (host != null) {
                host.setChanged();
            }
        }
    }

    private void updateResult() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = serverPlayer.server.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, serverPlayer.level())
                .filter(holder -> resultSlots.setRecipeUsed(serverPlayer.level(), serverPlayer, holder))
                .map(holder -> holder.value().assemble(input, serverPlayer.level().registryAccess()))
                .orElse(ItemStack.EMPTY);
        resultSlots.setItem(0, result);
        setRemoteSlot(resultIndex, result);
        serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(containerId, incrementStateId(), resultIndex, result));
    }

    /** Puts one of each used ingredient back into slots the last craft emptied. */
    private void refill(ItemKey[] before) {
        StorageNetwork network = network();
        if (network == null || !network.isActive()) {
            return;
        }
        refilling = true;
        try {
            for (int i = 0; i < 9; i++) {
                if (before[i] != null && craftSlots.getItem(i).isEmpty()) {
                    ItemStack refill = network.extract(before[i], 1);
                    if (!refill.isEmpty()) {
                        craftSlots.setItem(i, refill);
                    }
                }
            }
        } finally {
            refilling = false;
        }
        updateResult();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_CLEAR) {
            clearGrid(player);
            return true;
        }
        return false;
    }

    private void clearGrid(Player player) {
        StorageNetwork network = network();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = craftSlots.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = network != null ? network.insert(stack) : stack;
            if (!rest.isEmpty()) {
                player.getInventory().placeItemBackInInventory(rest);
            }
            craftSlots.setItem(i, ItemStack.EMPTY);
        }
        updateResult();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!persistent && !player.level().isClientSide) {
            clearGrid(player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == resultIndex) {
            Slot slot = slots.get(index);
            if (!slot.hasItem()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();
            stack.getItem().onCraftedBy(stack, player.level(), player);
            if (!moveItemStackTo(stack, playerStart, playerEnd(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == original.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
            player.drop(stack, false);
            return original;
        }
        if (index >= craftStart && index < resultIndex) {
            Slot slot = slots.get(index);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && !player.level().isClientSide) {
                StorageNetwork network = network();
                ItemStack rest = network != null ? network.insert(stack.copy()) : stack.copy();
                if (!rest.isEmpty() && !moveItemStackTo(rest, playerStart, playerEnd(), false)) {
                    slot.set(rest);
                    return ItemStack.EMPTY;
                }
                slot.set(ItemStack.EMPTY);
            }
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    private class TerminalResultSlot extends ResultSlot {
        TerminalResultSlot(Player player, CraftingContainer craftSlots, Container resultSlots, int x, int y) {
            super(player, craftSlots, resultSlots, 0, x, y);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            ItemKey[] before = new ItemKey[9];
            for (int i = 0; i < 9; i++) {
                before[i] = ItemKey.of(craftSlots.getItem(i));
            }
            super.onTake(player, stack);
            if (!player.level().isClientSide) {
                refill(before);
            }
        }
    }
}
