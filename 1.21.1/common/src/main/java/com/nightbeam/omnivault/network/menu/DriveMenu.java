package com.nightbeam.omnivault.network.menu;

import com.nightbeam.omnivault.menu.OVMenu;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.block.DriveBlockEntity;
import com.nightbeam.omnivault.network.cell.CellItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class DriveMenu extends OVMenu {
    public static final int CELL_X = 71;
    public static final int CELL_Y = 20;
    public static final int PLAYER_Y = 124;

    @Nullable
    private final DriveBlockEntity drive;
    private final DataSlot priority = DataSlot.standalone();

    public DriveMenu(int containerId, Inventory inventory, DriveBlockEntity drive) {
        this(containerId, inventory, drive.cells(), drive);
    }

    public static DriveMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        return new DriveMenu(containerId, inventory, new SimpleContainer(DriveBlockEntity.SLOTS), null);
    }

    private DriveMenu(int containerId, Inventory inventory, Container cells, @Nullable DriveBlockEntity drive) {
        super(NetworkContent.DRIVE_MENU.get(), containerId);
        this.drive = drive;
        for (int i = 0; i < DriveBlockEntity.SLOTS; i++) {
            addSlot(new Slot(cells, i, CELL_X + (i % 2) * 18, CELL_Y + (i / 2) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof CellItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addPlayerInventory(inventory, 8, PLAYER_Y);
        addDataSlot(priority);
        if (drive != null) {
            priority.set(drive.priority());
        }
    }

    public int priority() {
        return priority.get();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (drive == null) {
            return false;
        }
        int delta = switch (id) {
            case 0 -> -10;
            case 1 -> -1;
            case 2 -> 1;
            case 3 -> 10;
            default -> 0;
        };
        drive.setPriority(drive.priority() + delta);
        priority.set(drive.priority());
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return drive == null || (!drive.isRemoved() && player.distanceToSqr(drive.getBlockPos().getCenter()) <= 64);
    }
}
