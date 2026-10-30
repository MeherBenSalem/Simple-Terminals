package com.nightbeam.omnivault.util;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compacts and sorts a run of container slots: stacks of the same item are merged, then ordered by
 * creative-tab-like registry order (mod, then item id), larger stacks first.
 */
public final class InventorySorting {
    private InventorySorting() {
    }

    /** Menus that offer a sort button implement this; {@code section} lets one menu sort several areas. */
    public interface Sortable {
        void sortSection(int section);
    }

    public static void sort(Container container, int from, int to) {
        Map<ItemKey, Integer> totals = new LinkedHashMap<>();
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                totals.merge(ItemKey.of(stack), stack.getCount(), Integer::sum);
            }
        }
        List<ItemKey> keys = new ArrayList<>(totals.keySet());
        keys.sort(ORDER);
        int slot = from;
        for (ItemKey key : keys) {
            int remaining = totals.get(key);
            int max = Math.min(key.maxStackSize(), container.getMaxStackSize());
            while (remaining > 0 && slot < to) {
                int count = Math.min(max, remaining);
                container.setItem(slot++, key.toStack(count));
                remaining -= count;
            }
        }
        while (slot < to) {
            container.setItem(slot++, ItemStack.EMPTY);
        }
        container.setChanged();
    }

    public static final Comparator<ItemKey> ORDER = Comparator
            .comparing((ItemKey key) -> BuiltInRegistries.ITEM.getKey(key.item()).getNamespace().equals("minecraft") ? "" : BuiltInRegistries.ITEM.getKey(key.item()).getNamespace())
            .thenComparingInt(key -> BuiltInRegistries.ITEM.getId(key.item()))
            .thenComparing(key -> key.view().getHoverName().getString());

    /** Client asks the open menu to sort one of its sections. */
    public record SortPayload(int containerId, int section) implements CustomPacketPayload {
        public static final Type<SortPayload> TYPE = new Type<>(OmniVault.id("sort"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SortPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SortPayload::containerId,
                ByteBufCodecs.VAR_INT, SortPayload::section,
                SortPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void init() {
        OmniVaultCommon.packets(registrar -> registrar.toServer(SortPayload.TYPE, SortPayload.CODEC, (payload, player) -> {
            if (player.containerMenu.containerId == payload.containerId() && player.containerMenu instanceof Sortable sortable) {
                sortable.sortSection(payload.section());
                player.containerMenu.broadcastChanges();
            }
        }));
    }
}
