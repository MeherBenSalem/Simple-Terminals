package com.nightbeam.omnivault.drawer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * What a compacting drawer holds, carried by its item: the compression chain (most compressed first,
 * each with how many base items one of it is worth) and the stored amount in base items.
 */
public record CompactingContents(List<Tier> tiers, long amount) {
    public static final Codec<CompactingContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Tier.CODEC.listOf().fieldOf("tiers").forGetter(CompactingContents::tiers),
            Codec.LONG.optionalFieldOf("amount", 0L).forGetter(CompactingContents::amount)
    ).apply(i, CompactingContents::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompactingContents> STREAM_CODEC = StreamCodec.composite(
            Tier.STREAM_CODEC.apply(ByteBufCodecs.list()), CompactingContents::tiers,
            ByteBufCodecs.VAR_LONG, CompactingContents::amount,
            CompactingContents::new);

    public CompactingContents {
        tiers = List.copyOf(tiers);
    }

    public record Tier(ItemKey key, int unit) {
        public static final Codec<Tier> CODEC = RecordCodecBuilder.create(i -> i.group(
                DrawerContents.KEY_CODEC.fieldOf("item").forGetter(Tier::key),
                Codec.INT.fieldOf("unit").forGetter(Tier::unit)
        ).apply(i, Tier::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Tier> STREAM_CODEC = StreamCodec.composite(
                ItemKey.STREAM_CODEC, Tier::key,
                ByteBufCodecs.VAR_INT, Tier::unit,
                Tier::new);
    }
}
