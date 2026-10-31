package com.nightbeam.omnivault.backpack;

import com.nightbeam.omnivault.platform.Services;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Helpers for backpack stacks: identity, contents lookup, the storage rule and opening. */
public final class Backpacks {
    /** {@link Inventory} index of the chest armour slot. */
    public static final int CHEST_SLOT = Inventory.INVENTORY_SIZE + EquipmentSlot.CHEST.getIndex();
    public static final int OFFHAND_SLOT = Inventory.SLOT_OFFHAND;

    private Backpacks() {
    }

    public static boolean isBackpack(ItemStack stack) {
        return stack.getItem() instanceof BackpackItem;
    }

    @Nullable
    public static UUID getId(ItemStack stack) {
        return BackpackContent.BACKPACK_ID.get(stack);
    }

    /** The backpack's UUID, assigning a fresh one the first time it is needed. */
    public static UUID ensureId(ItemStack stack) {
        UUID id = getId(stack);
        if (id == null) {
            id = UUID.randomUUID();
            BackpackContent.BACKPACK_ID.set(stack, id);
        }
        return id;
    }

    public static BackpackTier tier(ItemStack stack) {
        return stack.getItem() instanceof BackpackItem item ? item.tier() : BackpackTier.LEATHER;
    }

    /** The contents of a backpack stack (server only), assigning a UUID if it has none yet. */
    public static BackpackData data(MinecraftServer server, ItemStack stack) {
        return BackpackStorage.get(server).getOrCreate(ensureId(stack), tier(stack));
    }

    /** The contents of a backpack stack if it was ever opened or filled, without creating anything. */
    @Nullable
    public static BackpackData existingData(MinecraftServer server, ItemStack stack) {
        UUID id = getId(stack);
        if (id == null) {
            return null;
        }
        BackpackData data = BackpackStorage.get(server).get(id);
        if (data != null) {
            data.ensureCapacity(tier(stack));
        }
        return data;
    }

    /**
     * What a backpack may hold: anything except backpacks, shulker boxes and other items that hold
     * items themselves (filled bundles, filled containers, the {@code omnivault:not_allowed_in_backpacks} tag).
     */
    public static boolean canStore(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (isBackpack(stack) || !stack.getItem().canFitInsideContainerItems() || stack.is(BackpackContent.NOT_ALLOWED_IN_BACKPACKS)) {
            return false;
        }
        // Filled shulker boxes and other block items keep their contents in BlockEntityTag.Items, bundles in Items.
        CompoundTag blockEntity = BlockItem.getBlockEntityData(stack);
        if (blockEntity != null && !blockEntity.getList("Items", Tag.TAG_COMPOUND).isEmpty()) {
            return false;
        }
        return !(stack.getItem() instanceof BundleItem) || BundleItem.getFullnessDisplay(stack) <= 0;
    }

    /** Updates the client-visible summary on {@code stack} if it changed. */
    public static void refreshSummary(ItemStack stack, BackpackData data) {
        BackpackSummary summary = BackpackSummary.of(data);
        if (!summary.equals(BackpackContent.SUMMARY.get(stack))) {
            BackpackContent.SUMMARY.set(stack, summary);
        }
    }

    /** Inventory slots holding backpacks: the chest slot first, then the hotbar and main inventory, then the off-hand. */
    public static List<Integer> carriedSlots(Player player) {
        Inventory inventory = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        if (isBackpack(inventory.getItem(CHEST_SLOT))) {
            slots.add(CHEST_SLOT);
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (isBackpack(inventory.getItem(slot))) {
                slots.add(slot);
            }
        }
        if (isBackpack(inventory.getItem(OFFHAND_SLOT))) {
            slots.add(OFFHAND_SLOT);
        }
        return slots;
    }

    /** The backpack the open-backpack key opens: the worn one, else the first one in the inventory; -1 for none. */
    public static int slotForKey(Player player) {
        List<Integer> slots = carriedSlots(player);
        return slots.isEmpty() ? -1 : slots.get(0);
    }

    public static void open(ServerPlayer player, BackpackSource source) {
        ItemStack stack = source.stack(player);
        if (!(stack.getItem() instanceof BackpackItem item)) {
            return;
        }
        UUID id = ensureId(stack);
        BackpackTier tier = item.tier();
        BackpackData data = BackpackStorage.get(player.server).getOrCreate(id, tier);
        refreshSummary(stack, data);
        BackpackBlockEntity placedBackpack = source instanceof BackpackSource.Placed placed ? placed.blockEntity(player.level()) : null;
        if (placedBackpack != null) {
            placedBackpack.setChanged();
        }
        Services.PLATFORM.openMenu(player,
                new SimpleMenuProvider((containerId, inventory, p) -> new BackpackMenu(containerId, inventory, data, tier, source, id), stack.getHoverName()),
                buf -> BackpackMenu.writeOpenData(buf, tier, data, source));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.7f, 1.15f);
    }
}
