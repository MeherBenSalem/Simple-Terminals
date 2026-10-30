package com.nightbeam.omnivault.drawer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * What a standard drawer holds, carried by its item when it is broken: one entry per compartment
 * with the item type (kept while locked and empty) and the count.
 */
public record DrawerContents(List<Entry> entries) {
    /** An item type as a data component value: a single item with its components. */
    public static final Codec<ItemKey> KEY_CODEC = ItemStack.SINGLE_ITEM_CODEC.xmap(ItemKey::of, ItemKey::toStack);

    public static final Codec<DrawerContents> CODEC = Entry.CODEC.listOf().xmap(DrawerContents::new, DrawerContents::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, DrawerContents> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(DrawerContents::new, DrawerContents::entries);

    public DrawerContents {
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.stream().allMatch(e -> e.key().isEmpty() || e.count() <= 0);
    }

    public record Entry(Optional<ItemKey> key, long count) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                KEY_CODEC.optionalFieldOf("item").forGetter(Entry::key),
                Codec.LONG.optionalFieldOf("count", 0L).forGetter(Entry::count)
        ).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ItemKey.STREAM_CODEC), Entry::key,
                ByteBufCodecs.VAR_LONG, Entry::count,
                Entry::new);
    }
}
