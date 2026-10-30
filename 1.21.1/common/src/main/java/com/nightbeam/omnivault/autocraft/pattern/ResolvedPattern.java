package com.nightbeam.omnivault.autocraft.pattern;

import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A pattern prepared for planning and execution: inputs and outputs merged per item type, and for
 * crafting patterns the matching recipe, looked up on demand and re-checked after reloads.
 */
public final class ResolvedPattern {
    private final PatternData data;
    private final List<KeyAmount> inputs;
    private final List<KeyAmount> outputs;
    // Crafting recipe cache, valid for one recipe manager instance (a /reload replaces it).
    @Nullable
    private RecipeManager cachedManager;
    @Nullable
    private CraftResult cachedResult;

    public ResolvedPattern(PatternData data) {
        this.data = data;
        this.inputs = aggregate(data.inputs());
        this.outputs = aggregate(data.outputs());
    }

    private static List<KeyAmount> aggregate(List<ItemStack> stacks) {
        Map<ItemKey, Long> merged = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            ItemKey key = ItemKey.of(stack);
            if (key != null) {
                merged.merge(key, (long) stack.getCount(), Long::sum);
            }
        }
        List<KeyAmount> out = new ArrayList<>(merged.size());
        merged.forEach((key, amount) -> out.add(new KeyAmount(key, amount)));
        return Collections.unmodifiableList(out);
    }

    public PatternData data() {
        return data;
    }

    public boolean processing() {
        return data.processing();
    }

    public List<KeyAmount> inputs() {
        return inputs;
    }

    public List<KeyAmount> outputs() {
        return outputs;
    }

    /** How many of {@code key} one run of this pattern makes. */
    public long outputAmount(ItemKey key) {
        for (KeyAmount output : outputs) {
            if (output.key().equals(key)) {
                return output.amount();
            }
        }
        return 0;
    }

    /**
     * Runs the crafting recipe once (without consuming anything) and reports its result and
     * container remainders, or null when no recipe matches the pattern any more.
     */
    @Nullable
    public CraftResult craft(ServerLevel level) {
        if (data.processing()) {
            return null;
        }
        RecipeManager manager = level.getServer().getRecipeManager();
        if (manager != cachedManager) {
            cachedManager = manager;
            cachedResult = resolve(level, manager);
        }
        return cachedResult;
    }

    @Nullable
    private CraftResult resolve(ServerLevel level, RecipeManager manager) {
        CraftingInput input = CraftingInput.of(3, 3, data.inputs());
        RecipeHolder<CraftingRecipe> holder = manager.getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
        if (holder == null) {
            return null;
        }
        ItemStack result = holder.value().assemble(input, level.registryAccess());
        // The recipe must still make what the pattern promises.
        if (result.isEmpty() || !ItemStack.matches(result, data.primaryOutput())) {
            return null;
        }
        List<KeyAmount> remainders = new ArrayList<>();
        NonNullList<ItemStack> remaining = holder.value().getRemainingItems(input);
        Map<ItemKey, Long> merged = new LinkedHashMap<>();
        for (ItemStack stack : remaining) {
            ItemKey key = ItemKey.of(stack);
            if (key != null) {
                merged.merge(key, (long) stack.getCount(), Long::sum);
            }
        }
        merged.forEach((key, amount) -> remainders.add(new KeyAmount(key, amount)));
        return new CraftResult(ItemKey.of(result), result.getCount(), List.copyOf(remainders));
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ResolvedPattern other && data.equals(other.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }

    @Override
    public String toString() {
        return data.toString();
    }

    public record KeyAmount(ItemKey key, long amount) {
    }

    /** One run of a crafting recipe: its result and what stays behind in the grid (empty buckets...). */
    public record CraftResult(ItemKey result, long count, List<KeyAmount> remainders) {
    }
}
