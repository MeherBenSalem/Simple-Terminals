package com.nightbeam.omnivault.physical;

import com.nightbeam.omnivault.menu.OVMenu;
import com.nightbeam.omnivault.physical.linked.LinkedAccess;
import com.nightbeam.omnivault.physical.linked.LinkedFrequency;
import com.nightbeam.omnivault.physical.vault.VaultTier;
import com.nightbeam.omnivault.util.InventorySorting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * The grid menu used by tiered vaults (45–162 slots), Linked Vaults and Linked Pouches (27 slots).
 * Slots map 1:1 onto {@code container}; the {@link Source} decides validity and receives open/close
 * notifications (that is what drives the lid animation and sounds).
 */
public class StorageMenu extends OVMenu implements InventorySorting.Sortable {
    /** What the menu was opened from: a vault block entity, a linked vault, or a pouch in hand. */
    @FunctionalInterface
    public interface Source {
        boolean stillValid(Player player);

        default void startOpen(Player player) {
        }

        default void stopOpen(Player player) {
        }
    }

    private final Container container;
    private final StorageLayout layout;
    private final Source source;
    @Nullable
    private final VaultTier tier;
    @Nullable
    private final LinkedFrequency frequency;

    public StorageMenu(@Nullable MenuType<?> type, int containerId, Inventory inventory, Container container, StorageLayout layout,
                       Source source, @Nullable VaultTier tier, @Nullable LinkedFrequency frequency) {
        super(type, containerId);
        checkContainerSize(container, layout.size());
        this.container = container;
        this.layout = layout;
        this.source = source;
        this.tier = tier;
        this.frequency = frequency;
        source.startOpen(inventory.player);
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                addSlot(new Slot(container, column + row * layout.columns(), layout.gridX() + column * 18, layout.gridY() + row * 18));
            }
        }
        addPlayerInventory(inventory, layout.inventoryX(), layout.inventoryY());
    }

    /** Client side of a tiered vault: the tier comes from the open-menu data. */
    public static StorageMenu vaultClient(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        VaultTier tier = VaultTier.byId(data.readVarInt());
        return new StorageMenu(PhysicalContent.VAULT_MENU.get(), containerId, inventory, new SimpleContainer(tier.size()), tier.layout(),
                player -> true, tier, null);
    }

    /** Client side of a Linked Vault or Pouch: the frequency is shown in the title bar. */
    public static StorageMenu linkedClient(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        LinkedFrequency frequency = LinkedFrequency.STREAM_CODEC.decode(data);
        StorageLayout layout = LinkedAccess.LAYOUT;
        return new StorageMenu(PhysicalContent.LINKED_MENU.get(), containerId, inventory, new SimpleContainer(layout.size()), layout,
                player -> true, null, frequency);
    }

    public Container container() {
        return container;
    }

    public StorageLayout layout() {
        return layout;
    }

    public Source source() {
        return source;
    }

    @Nullable
    public VaultTier tier() {
        return tier;
    }

    @Nullable
    public LinkedFrequency frequency() {
        return frequency;
    }

    @Override
    public boolean stillValid(Player player) {
        return source.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        source.stopOpen(player);
    }

    @Override
    public void sortSection(int section) {
        InventorySorting.sort(container, 0, container.getContainerSize());
    }
}
