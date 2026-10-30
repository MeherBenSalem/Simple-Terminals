package com.nightbeam.omnivault.autocraft.pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What an encoded pattern holds. A <b>crafting</b> pattern is a 3×3 grid (nine entries, empty ones
 * allowed) and the single result its recipe produced when it was encoded. A <b>processing</b>
 * pattern lists up to nine input stacks and up to three output stacks for any machine.
 *
 * <p>Instances are immutable data component values; never modify the stacks they return.
 */
public final class PatternData {
    public static final int MAX_INPUTS = 9;
    public static final int MAX_OUTPUTS = 3;
    /** Largest amount one processing pattern slot can ask for. */
    public static final int MAX_SLOT_AMOUNT = 64;

    public static final Codec<PatternData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("processing").forGetter(PatternData::processing),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inputs").forGetter(PatternData::inputs),
            ItemStack.CODEC.listOf().fieldOf("outputs").forGetter(PatternData::outputs)
    ).apply(instance, PatternData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PatternData::processing,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, PatternData::inputs,
            ItemStack.LIST_STREAM_CODEC, PatternData::outputs,
            PatternData::new);

    private final boolean processing;
    private final List<ItemStack> inputs;
    private final List<ItemStack> outputs;
    private final int hash;

    public PatternData(boolean processing, List<ItemStack> inputs, List<ItemStack> outputs) {
        this.processing = processing;
        this.inputs = copy(inputs);
        this.outputs = copy(outputs);
        this.hash = Objects.hash(processing, ItemStack.hashStackList(this.inputs), ItemStack.hashStackList(this.outputs),
                counts(this.inputs), counts(this.outputs));
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            out.add(stack.copy());
        }
        return List.copyOf(out);
    }

    private static int counts(List<ItemStack> stacks) {
        int h = 1;
        for (ItemStack stack : stacks) {
            h = 31 * h + stack.getCount();
        }
        return h;
    }

    /** A crafting pattern from a 3×3 grid (row by row) and the recipe's result. */
    public static PatternData crafting(List<ItemStack> grid, ItemStack result) {
        List<ItemStack> nine = new ArrayList<>(MAX_INPUTS);
        for (int i = 0; i < MAX_INPUTS; i++) {
            ItemStack stack = i < grid.size() ? grid.get(i) : ItemStack.EMPTY;
            nine.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        return new PatternData(false, nine, List.of(result));
    }

    /** A processing pattern; empty entries are dropped. Returns null without any input or output. */
    @Nullable
    public static PatternData processing(List<ItemStack> inputs, List<ItemStack> outputs) {
        List<ItemStack> in = inputs.stream().filter(s -> !s.isEmpty()).limit(MAX_INPUTS).toList();
        List<ItemStack> out = outputs.stream().filter(s -> !s.isEmpty()).limit(MAX_OUTPUTS).toList();
        if (in.isEmpty() || out.isEmpty()) {
            return null;
        }
        return new PatternData(true, in, out);
    }

    public boolean processing() {
        return processing;
    }

    /** Crafting: the nine grid entries (possibly empty). Processing: the non-empty input stacks. */
    public List<ItemStack> inputs() {
        return inputs;
    }

    public List<ItemStack> outputs() {
        return outputs;
    }

    public ItemStack primaryOutput() {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0);
    }

    public boolean isValid() {
        if (outputs.isEmpty() || outputs.stream().anyMatch(ItemStack::isEmpty)) {
            return false;
        }
        if (processing) {
            return !inputs.isEmpty() && inputs.size() <= MAX_INPUTS && outputs.size() <= MAX_OUTPUTS
                    && inputs.stream().noneMatch(ItemStack::isEmpty);
        }
        return inputs.size() == MAX_INPUTS && outputs.size() == 1 && inputs.stream().anyMatch(s -> !s.isEmpty());
    }

    public Tag save(HolderLookup.Provider registries) {
        return CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this).getOrThrow();
    }

    @Nullable
    public static PatternData load(HolderLookup.Provider registries, Tag tag) {
        return CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof PatternData other && hash == other.hash && processing == other.processing
                && ItemStack.listMatches(inputs, other.inputs) && ItemStack.listMatches(outputs, other.outputs);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return (processing ? "Processing" : "Crafting") + "Pattern" + inputs + " -> " + outputs;
    }
}
