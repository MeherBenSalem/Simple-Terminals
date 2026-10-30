package com.nightbeam.omnivault.network.menu;

import com.nightbeam.omnivault.menu.GhostSlot;
import com.nightbeam.omnivault.menu.OVMenu;
import com.nightbeam.omnivault.network.NetworkContent;
import com.nightbeam.omnivault.network.NetworkPackets;
import com.nightbeam.omnivault.network.block.SignalEmitterBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SignalEmitterMenu extends OVMenu implements NetworkPackets.ValueReceiver {
    public static final int VALUE_THRESHOLD = 0;
    public static final int BUTTON_MODE = 0;
    public static final int PLAYER_Y = 84;

    @Nullable
    private final SignalEmitterBlockEntity emitter;
    private final Container filter;
    private long threshold;
    private boolean below;

    public SignalEmitterMenu(int containerId, Inventory inventory, SignalEmitterBlockEntity emitter) {
        this(containerId, inventory, emitter, emitter.filter(), emitter.threshold(), emitter.below());
    }

    public static SignalEmitterMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        data.readBlockPos();
        return new SignalEmitterMenu(containerId, inventory, null, new SimpleContainer(1), data.readVarLong(), data.readBoolean());
    }

    private SignalEmitterMenu(int containerId, Inventory inventory, @Nullable SignalEmitterBlockEntity emitter, Container filter, long threshold, boolean below) {
        super(NetworkContent.SIGNAL_EMITTER_MENU.get(), containerId);
        this.emitter = emitter;
        this.filter = filter;
        this.threshold = threshold;
        this.below = below;
        addSlot(new GhostSlot(filter, 0, 26, 35));
        addPlayerInventory(inventory, 8, PLAYER_Y);
    }

    public long threshold() {
        return threshold;
    }

    public boolean below() {
        return below;
    }

    /** Client-side mirror after the player toggles the mode. */
    public void setBelowClient(boolean below) {
        this.below = below;
    }

    public void setThresholdClient(long threshold) {
        this.threshold = threshold;
    }

    @Override
    public void receiveValue(int key, long value) {
        if (emitter != null && key == VALUE_THRESHOLD) {
            emitter.setThreshold(value);
            threshold = emitter.threshold();
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (emitter != null && id == BUTTON_MODE) {
            emitter.setBelow(!emitter.below());
            below = emitter.below();
            return true;
        }
        return false;
    }

    @Override
    protected boolean moveIntoOwnSlots(ItemStack stack) {
        filter.setItem(0, stack.copyWithCount(1));
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return emitter == null || (!emitter.isRemoved() && player.distanceToSqr(emitter.getBlockPos().getCenter()) <= 64);
    }
}
