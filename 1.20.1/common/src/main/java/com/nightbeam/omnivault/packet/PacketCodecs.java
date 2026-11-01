package com.nightbeam.omnivault.packet;

import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Codecs for common values. */
public final class PacketCodecs {
    public static final PacketCodec<Boolean> BOOL = PacketCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean);
    public static final PacketCodec<Integer> VAR_INT = PacketCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt);
    public static final PacketCodec<Long> VAR_LONG = PacketCodec.of(FriendlyByteBuf::writeVarLong, FriendlyByteBuf::readVarLong);
    public static final PacketCodec<String> STRING_UTF8 = PacketCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf);
    public static final PacketCodec<UUID> UUID = PacketCodec.of(FriendlyByteBuf::writeUUID, FriendlyByteBuf::readUUID);
    public static final PacketCodec<CompoundTag> COMPOUND_TAG = PacketCodec.of(FriendlyByteBuf::writeNbt, buf -> {
        CompoundTag tag = buf.readNbt();
        return tag == null ? new CompoundTag() : tag;
    });
    /**
     * A stack of any size, or the empty stack. {@code FriendlyByteBuf#writeItem} sends the count as a
     * byte, which cuts off counts above 127.
     */
    public static final PacketCodec<ItemStack> ITEM_STACK = PacketCodec.of((buf, stack) -> {
        if (stack.isEmpty()) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(stack.getCount());
        buf.writeId(BuiltInRegistries.ITEM, stack.getItem());
        buf.writeNbt(stack.getTag());
    }, buf -> {
        int count = buf.readVarInt();
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        Item item = buf.readById(BuiltInRegistries.ITEM);
        ItemStack stack = new ItemStack(item == null ? Items.AIR : item, count);
        stack.setTag(buf.readNbt());
        return stack;
    });

    private PacketCodecs() {
    }

    /** Entries of a built-in registry, by their numeric id. */
    @SuppressWarnings("unchecked")
    public static <T> PacketCodec<T> registry(ResourceKey<? extends Registry<T>> key) {
        Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
        if (registry == null) {
            throw new IllegalArgumentException("Unknown registry " + key.location());
        }
        return idMapper(registry);
    }

    public static <T> PacketCodec<T> idMapper(IdMap<T> ids) {
        return PacketCodec.of((buf, value) -> buf.writeId(ids, value), buf -> buf.readById(ids));
    }

    public static <T> Function<PacketCodec<T>, PacketCodec<List<T>>> list() {
        return list(Integer.MAX_VALUE);
    }

    /** A list of at most {@code max} entries; longer lists are refused when read. */
    public static <T> Function<PacketCodec<T>, PacketCodec<List<T>>> list(int max) {
        return codec -> PacketCodec.of((buf, list) -> {
            if (list.size() > max) {
                throw new IllegalArgumentException("List too long: " + list.size() + " > " + max);
            }
            buf.writeVarInt(list.size());
            for (T value : list) {
                codec.encode(buf, value);
            }
        }, buf -> {
            int size = buf.readVarInt();
            if (size < 0 || size > max) {
                throw new IllegalArgumentException("List too long: " + size + " > " + max);
            }
            List<T> list = new ArrayList<>(Math.min(size, 65536));
            for (int i = 0; i < size; i++) {
                list.add(codec.decode(buf));
            }
            return list;
        });
    }

    public static <T> PacketCodec<Optional<T>> optional(PacketCodec<T> codec) {
        return PacketCodec.of((buf, value) -> {
            buf.writeBoolean(value.isPresent());
            value.ifPresent(v -> codec.encode(buf, v));
        }, buf -> buf.readBoolean() ? Optional.of(codec.decode(buf)) : Optional.empty());
    }

    public static <K, V, M extends Map<K, V>> PacketCodec<M> map(IntFunction<M> factory, PacketCodec<K> keys, PacketCodec<V> values) {
        return PacketCodec.of((buf, map) -> {
            buf.writeVarInt(map.size());
            map.forEach((k, v) -> {
                keys.encode(buf, k);
                values.encode(buf, v);
            });
        }, buf -> {
            int size = buf.readVarInt();
            M map = factory.apply(Math.min(size, 65536));
            for (int i = 0; i < size; i++) {
                map.put(keys.decode(buf), values.decode(buf));
            }
            return map;
        });
    }
}
