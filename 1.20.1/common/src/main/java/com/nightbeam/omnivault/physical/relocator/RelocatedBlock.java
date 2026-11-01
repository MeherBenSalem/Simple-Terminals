package com.nightbeam.omnivault.physical.relocator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

/** A block picked up by a Relocator: its state and its block entity data (without position or id). */
public record RelocatedBlock(BlockState state, CompoundTag data) {
    public static final Codec<RelocatedBlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.fieldOf("state").forGetter(RelocatedBlock::state),
            CompoundTag.CODEC.fieldOf("data").forGetter(RelocatedBlock::data)
    ).apply(instance, RelocatedBlock::new));

    /** Number of item stacks in the usual "Items" list, for the tooltip; -1 if the block stores none. */
    public int storedStacks() {
        return data.contains("Items", Tag.TAG_LIST) ? data.getList("Items", Tag.TAG_COMPOUND).size() : -1;
    }
}
