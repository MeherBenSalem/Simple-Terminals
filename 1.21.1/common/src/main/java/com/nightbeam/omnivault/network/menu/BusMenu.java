package com.nightbeam.omnivault.network.menu;

import com.nightbeam.omnivault.item.UpgradeItem;
import com.nightbeam.omnivault.menu.GhostSlot;
import com.nightbeam.omnivault.menu.OVMenu;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.block.BusBlockEntity;
import com.nightbeam.omnivault.network.block.StorageBusBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Shared screen for import, export and storage buses: filter, upgrades and mode buttons. */
public class BusMenu extends OVMenu {
    public enum Kind {
        IMPORT, EXPORT, STORAGE, DESTRUCTOR, CONSTRUCTOR
    }

    public static final int FILTER_X = 62;
    public static final int FILTER_Y = 22;
    public static final int UPGRADE_X = 152;
    public static final int PLAYER_Y = 98;
    public static final int BUTTON_MODE = 0;
    public static final int BUTTON_ACCESS = 1;
    public static final int BUTTON_PRIORITY_BASE = 2; // 2..5: -10, -1, +1, +10

    @Nullable
    private final BusBlockEntity bus;
    private final Kind kind;
    private final Container filter;
    private final ContainerData data;

    public BusMenu(int containerId, Inventory inventory, BusBlockEntity bus) {
        this(containerId, inventory, bus, bus.kind(), bus.filter(), bus.upgrades(), new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> bus.denyList() ? 1 : 0;
                    case 1 -> bus instanceof StorageBusBlockEntity storage ? storage.access().ordinal() : 0;
                    case 2 -> bus instanceof StorageBusBlockEntity storage ? storage.priority() : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 3;
            }
        });
    }

    public static BusMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        data.readBlockPos();
        Kind kind = Kind.values()[data.readVarInt()];
        return new BusMenu(containerId, inventory, null, kind, new SimpleContainer(BusBlockEntity.FILTER_SLOTS),
                new SimpleContainer(BusBlockEntity.UPGRADE_SLOTS), new SimpleContainerData(3));
    }

    private BusMenu(int containerId, Inventory inventory, @Nullable BusBlockEntity bus, Kind kind, Container filter, Container upgrades, ContainerData data) {
        super(NetworkContent.BUS_MENU.get(), containerId);
        this.bus = bus;
        this.kind = kind;
        this.filter = filter;
        this.data = data;
        for (int i = 0; i < BusBlockEntity.FILTER_SLOTS; i++) {
            addSlot(new GhostSlot(filter, i, FILTER_X + (i % 3) * 18, FILTER_Y + (i / 3) * 18));
        }
        for (int i = 0; i < BusBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new Slot(upgrades, i, UPGRADE_X, 8 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return upgrades.canPlaceItem(getContainerSlot(), stack) && stack.getItem() instanceof UpgradeItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addPlayerInventory(inventory, 8, PLAYER_Y);
        addDataSlots(data);
    }

    public Kind kind() {
        return kind;
    }

    public boolean denyList() {
        return data.get(0) == 1;
    }

    public StorageBusBlockEntity.Access access() {
        return StorageBusBlockEntity.Access.values()[Math.floorMod(data.get(1), StorageBusBlockEntity.Access.values().length)];
    }

    public int priority() {
        // Data slots travel as shorts; priorities stay within ±999.
        return (short) data.get(2);
    }

    @Override
    protected boolean moveIntoOwnSlots(ItemStack stack) {
        if (stack.getItem() instanceof UpgradeItem) {
            return moveItemStackTo(stack, BusBlockEntity.FILTER_SLOTS, BusBlockEntity.FILTER_SLOTS + BusBlockEntity.UPGRADE_SLOTS, false);
        }
        addGhost(filter, stack);
        return false;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (bus == null) {
            return false;
        }
        if (id == BUTTON_MODE) {
            bus.setDenyList(!bus.denyList());
            return true;
        }
        if (bus instanceof StorageBusBlockEntity storage) {
            if (id == BUTTON_ACCESS) {
                storage.cycleAccess();
                return true;
            }
            int[] deltas = {-10, -1, 1, 10};
            int index = id - BUTTON_PRIORITY_BASE;
            if (index >= 0 && index < deltas.length) {
                storage.setPriority(storage.priority() + deltas[index]);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return bus == null || (!bus.isRemoved() && player.distanceToSqr(bus.getBlockPos().getCenter()) <= 64);
    }
}
