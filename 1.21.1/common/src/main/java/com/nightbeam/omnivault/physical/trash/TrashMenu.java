package com.nightbeam.omnivault.physical.trash;

import com.nightbeam.omnivault.menu.OVMenu;
import com.nightbeam.omnivault.physical.PhysicalContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/** Trash can GUI: a void slot, the recoverable last voided stack, and a 9-entry automation filter. */
public class TrashMenu extends OVMenu {
    public static final int INPUT = 0;
    public static final int RECOVERY = 1;
    public static final int FILTER_START = 2;
    public static final int FILTER_END = FILTER_START + TrashInventory.FILTER_SLOTS;
    public static final int BUTTON_MODE = 0;
    public static final int BUTTON_CLEAR = 1;

    public static final int INPUT_X = 35;
    public static final int RECOVERY_X = 125;
    public static final int TOP_ROW_Y = 28;
    public static final int FILTER_Y = 80;
    public static final int INVENTORY_Y = 114;
    public static final int HEIGHT = INVENTORY_Y + 82;

    private final TrashInventory inventory;
    private final Predicate<Player> validity;
    private final Player player;
    @Nullable
    private final BlockPos pos;

    public TrashMenu(int containerId, Inventory playerInventory, TrashInventory inventory, Predicate<Player> validity, @Nullable BlockPos pos) {
        super(PhysicalContent.TRASH_MENU.get(), containerId);
        this.inventory = inventory;
        this.validity = validity;
        this.player = playerInventory.player;
        this.pos = pos;
        addSlot(new Slot(inventory.input, 0, INPUT_X, TOP_ROW_Y) {
            @Override
            public void set(ItemStack stack) {
                if (!stack.isEmpty() && TrashMenu.this.pos != null) {
                    TrashMenu.this.player.level().playSound(null, TrashMenu.this.pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.25f,
                            1.6f + TrashMenu.this.player.getRandom().nextFloat() * 0.3f);
                }
                super.set(stack);
            }
        });
        addSlot(new Slot(inventory.recovery, 0, RECOVERY_X, TOP_ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int i = 0; i < TrashInventory.FILTER_SLOTS; i++) {
            addSlot(new GhostSlot(inventory.filterContainer, i, 8 + i * 18, FILTER_Y));
        }
        addPlayerInventory(playerInventory, 8, INVENTORY_Y);
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return inventory.allowList() ? 1 : 0;
            }

            @Override
            public void set(int value) {
                inventory.setAllowList(value != 0);
            }
        });
    }

    public static TrashMenu client(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        return new TrashMenu(containerId, playerInventory, new TrashInventory(() -> {
        }), p -> true, null);
    }

    public TrashInventory inventory() {
        return inventory;
    }

    @Override
    public boolean stillValid(Player player) {
        return validity.test(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_MODE) {
            inventory.setAllowList(!inventory.allowList());
            return true;
        }
        if (id == BUTTON_CLEAR) {
            inventory.clearFilter();
            return true;
        }
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= FILTER_START && slotId < FILTER_END) {
            // Ghost slots: clicking with an item sets a filter entry, clicking empty-handed clears it.
            if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
                ItemStack carried = getCarried();
                slots.get(slotId).set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= FILTER_START && index < FILTER_END) {
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    @Override
    protected boolean moveIntoOwnSlots(ItemStack stack) {
        // Shift-clicking from the inventory trashes the stack.
        return moveItemStackTo(stack, INPUT, INPUT + 1, false);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostSlot) && super.canDragTo(slot);
    }

    /** A filter entry: shows an item but never holds one. */
    public static final class GhostSlot extends Slot {
        GhostSlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
