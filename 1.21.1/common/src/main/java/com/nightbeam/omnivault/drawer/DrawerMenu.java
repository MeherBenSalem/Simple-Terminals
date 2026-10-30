package com.nightbeam.omnivault.drawer;

import com.nightbeam.omnivault.menu.OVMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The drawer's upgrade menu: four upgrade slots, a lock toggle and the player inventory. */
public class DrawerMenu extends OVMenu {
    public static final int BUTTON_TOGGLE_LOCK = 0;
    public static final int UPGRADE_X = 98;
    public static final int UPGRADE_Y = 72;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 106;

    private final BlockPos pos;
    @Nullable
    private final AbstractDrawerBlockEntity drawer;
    private final SimpleContainer upgrades;

    /** Client side: the drawer is looked up in the client level for display only. */
    public DrawerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos(), null);
    }

    /** Server side. */
    public DrawerMenu(int containerId, Inventory inventory, AbstractDrawerBlockEntity drawer) {
        this(containerId, inventory, drawer.getBlockPos(), drawer);
    }

    private DrawerMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable AbstractDrawerBlockEntity serverDrawer) {
        super(DrawerContent.DRAWER_MENU.get(), containerId);
        this.pos = pos;
        if (serverDrawer != null) {
            this.drawer = serverDrawer;
            this.upgrades = serverDrawer.upgrades();
        } else {
            this.drawer = inventory.player.level().getBlockEntity(pos) instanceof AbstractDrawerBlockEntity client ? client : null;
            this.upgrades = new SimpleContainer(AbstractDrawerBlockEntity.UPGRADE_SLOTS);
        }
        for (int i = 0; i < AbstractDrawerBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new UpgradeSlot(upgrades, i, UPGRADE_X + i * 18, UPGRADE_Y));
        }
        addPlayerInventory(inventory, INVENTORY_X, INVENTORY_Y);
    }

    public BlockPos pos() {
        return pos;
    }

    /** The drawer (on the client, its synced copy) or null if it is gone. */
    @Nullable
    public AbstractDrawerBlockEntity drawer() {
        return drawer;
    }

    @Override
    public boolean stillValid(Player player) {
        return drawer != null && !drawer.isRemoved() && Container.stillValidBlockEntity(drawer, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE_LOCK && drawer != null) {
            if (!player.level().isClientSide) {
                DrawerInteraction.toggleLock(drawer, player);
            }
            return true;
        }
        return false;
    }

    private final class UpgradeSlot extends Slot {
        private UpgradeSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return AbstractDrawerBlockEntity.isValidUpgrade(upgrades, getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPickup(Player player) {
            // The server decides with real contents; the client copy may lag a tick behind.
            return drawer == null || player.level().isClientSide || drawer.canRemoveUpgrade(getContainerSlot());
        }
    }
}
