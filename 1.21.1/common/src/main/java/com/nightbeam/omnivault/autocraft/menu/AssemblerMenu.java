package com.nightbeam.omnivault.autocraft.menu;

import com.nightbeam.omnivault.autocraft.AutocraftConfig;
import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.block.AssemblerBlockEntity;
import com.nightbeam.omnivault.autocraft.block.AutocraftDeviceBlock;
import com.nightbeam.omnivault.item.UpgradeItem;
import com.nightbeam.omnivault.menu.OVMenu;
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

/** The Assembler's upgrade slots and its speed readout. */
public class AssemblerMenu extends OVMenu {
    public static final int UPGRADE_X = 152;
    public static final int PLAYER_Y = 92;

    @Nullable
    private final AssemblerBlockEntity assembler;
    private final ContainerData data;

    public AssemblerMenu(int containerId, Inventory inventory, AssemblerBlockEntity assembler) {
        this(containerId, inventory, assembler.upgrades(), assembler, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> assembler.craftsPerCycle();
                    case 1 -> assembler.getBlockState().getValue(AutocraftDeviceBlock.WORKING) ? 1 : 0;
                    case 2 -> assembler.isOnline() ? 1 : 0;
                    case 3 -> AutocraftConfig.ASSEMBLER_INTERVAL.get();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 4;
            }
        });
    }

    public static AssemblerMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        data.readBlockPos();
        return new AssemblerMenu(containerId, inventory, new SimpleContainer(AssemblerBlockEntity.UPGRADE_SLOTS), null, new SimpleContainerData(4));
    }

    private AssemblerMenu(int containerId, Inventory inventory, Container upgrades, @Nullable AssemblerBlockEntity assembler, ContainerData data) {
        super(AutocraftContent.ASSEMBLER_MENU.get(), containerId);
        this.assembler = assembler;
        this.data = data;
        for (int i = 0; i < AssemblerBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new Slot(upgrades, i, UPGRADE_X, 8 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof UpgradeItem upgrade && upgrade.kind() == UpgradeItem.Kind.SPEED;
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

    public int craftsPerCycle() {
        return data.get(0);
    }

    public boolean working() {
        return data.get(1) == 1;
    }

    public boolean online() {
        return data.get(2) == 1;
    }

    public int interval() {
        return data.get(3);
    }

    @Override
    public boolean stillValid(Player player) {
        return assembler == null || (!assembler.isRemoved() && player.distanceToSqr(assembler.getBlockPos().getCenter()) <= 64);
    }
}
