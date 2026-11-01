package com.nightbeam.omnivault.network.cell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Summary of a cell's contents, mirrored onto the item so clients can show it. */
public record CellUsage(long stored, int types) {
    public static final CellUsage EMPTY = new CellUsage(0, 0);
    public static final Codec<CellUsage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("stored").forGetter(CellUsage::stored),
            Codec.INT.fieldOf("types").forGetter(CellUsage::types)
    ).apply(i, CellUsage::new));
    public static final StreamCodec<ByteBuf, CellUsage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, CellUsage::stored,
            ByteBufCodecs.VAR_INT, CellUsage::types,
            CellUsage::new);
}
