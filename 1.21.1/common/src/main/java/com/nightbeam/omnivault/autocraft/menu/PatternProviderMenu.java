package com.nightbeam.omnivault.autocraft.menu;

import com.nightbeam.omnivault.autocraft.AutocraftContent;
import com.nightbeam.omnivault.autocraft.block.PatternProviderBlockEntity;
import com.nightbeam.omnivault.autocraft.pattern.PatternItem;
import com.nightbeam.omnivault.menu.OVMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class PatternProviderMenu extends OVMenu {
    public static final int SLOT_X = 62;
    public static final int SLOT_Y = 22;
    public static final int PLAYER_Y = 92;

    @Nullable
    private final PatternProviderBlockEntity provider;

    public PatternProviderMenu(int containerId, Inventory inventory, PatternProviderBlockEntity provider) {
        this(containerId, inventory, provider.patterns(), provider);
    }

    public static PatternProviderMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        data.readBlockPos();
        return new PatternProviderMenu(containerId, inventory, new SimpleContainer(PatternProviderBlockEntity.SLOTS), null);
    }

    private PatternProviderMenu(int containerId, Inventory inventory, Container patterns, @Nullable PatternProviderBlockEntity provider) {
        super(AutocraftContent.PATTERN_PROVIDER_MENU.get(), containerId);
        this.provider = provider;
        for (int i = 0; i < PatternProviderBlockEntity.SLOTS; i++) {
            addSlot(new Slot(patterns, i, SLOT_X + (i % 3) * 18, SLOT_Y + (i / 3) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return PatternItem.isEncoded(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addPlayerInventory(inventory, 8, PLAYER_Y);
    }

    @Override
    public boolean stillValid(Player player) {
        return provider == null || (!provider.isRemoved() && player.distanceToSqr(provider.getBlockPos().getCenter()) <= 64);
    }
}
