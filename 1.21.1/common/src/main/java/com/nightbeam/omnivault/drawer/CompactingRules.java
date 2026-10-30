package com.nightbeam.omnivault.drawer;

import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds compression chains (block / ingot / nugget) from the server's crafting recipes: a 3×3 or 2×2
 * grid of one item that crafts exactly one of another, confirmed by the reverse one-item recipe that
 * gives the same number back. A few vanilla materials whose blocks cannot be crafted back (quartz,
 * glowstone, clay, snow, amethyst) are allowed with only the compression recipe, which still keeps the
 * conversion lossless. Results are cached until the recipe manager changes (e.g. on /reload).
 */
public final class CompactingRules {
    /** Base item -> compressed item for the one-way vanilla recipes listed above. */
    private static final Map<ResourceLocation, ResourceLocation> ONE_WAY = Map.of(
            ResourceLocation.withDefaultNamespace("quartz"), ResourceLocation.withDefaultNamespace("quartz_block"),
            ResourceLocation.withDefaultNamespace("glowstone_dust"), ResourceLocation.withDefaultNamespace("glowstone"),
            ResourceLocation.withDefaultNamespace("clay_ball"), ResourceLocation.withDefaultNamespace("clay"),
            ResourceLocation.withDefaultNamespace("snowball"), ResourceLocation.withDefaultNamespace("snow_block"),
            ResourceLocation.withDefaultNamespace("amethyst_shard"), ResourceLocation.withDefaultNamespace("amethyst_block"));
    private static final int MAX_TIERS = 3;

    private static final Map<Item, Chain> CACHE = new HashMap<>();
    @Nullable
    private static RecipeManager cachedFor;

    private CompactingRules() {
    }

    /**
     * A compression chain, most compressed first. {@code units[i]} is how many of the last (base) item
     * one item of tier {@code i} is worth; the last unit is always 1.
     */
    public record Chain(List<ItemKey> keys, int[] units) {
        public int size() {
            return keys.size();
        }

        public int indexOf(ItemKey key) {
            return keys.indexOf(key);
        }
    }

    /** The chain containing {@code key}; items without a chain get a single tier. */
    public static Chain chainFor(Level level, ItemKey key) {
        if (!key.view().getComponentsPatch().isEmpty()) {
            return single(key);
        }
        RecipeManager recipes = level.getRecipeManager();
        synchronized (CACHE) {
            if (recipes != cachedFor) {
                CACHE.clear();
                cachedFor = recipes;
            }
            Chain cached = CACHE.get(key.item());
            if (cached == null) {
                cached = compute(level, key.item());
                for (ItemKey tier : cached.keys()) {
                    CACHE.putIfAbsent(tier.item(), cached);
                }
                CACHE.put(key.item(), cached);
            }
            return cached;
        }
    }

    private static Chain single(ItemKey key) {
        return new Chain(List.of(key), new int[]{1});
    }

    private static Chain compute(Level level, Item item) {
        // Walk down to smaller forms (block -> ingot -> nugget), then up from the start item.
        List<Conversion> lower = new ArrayList<>();
        Item current = item;
        while (lower.size() < MAX_TIERS - 1) {
            Optional<Conversion> down = decompress(level, current);
            if (down.isEmpty() || contains(lower, down.get().item()) || down.get().item() == item) {
                break;
            }
            lower.add(down.get());
            current = down.get().item();
        }
        List<Conversion> upper = new ArrayList<>();
        current = item;
        while (1 + lower.size() + upper.size() < MAX_TIERS) {
            Optional<Conversion> up = compress(level, current);
            if (up.isEmpty() || contains(upper, up.get().item()) || contains(lower, up.get().item()) || up.get().item() == item) {
                break;
            }
            upper.add(up.get());
            current = up.get().item();
        }

        // Tiers from most to least compressed: upper reversed, the item itself, then lower.
        List<Item> items = new ArrayList<>();
        List<Integer> ratios = new ArrayList<>(); // ratio between tier i and tier i + 1
        for (int i = upper.size() - 1; i >= 0; i--) {
            items.add(upper.get(i).item());
            ratios.add(upper.get(i).ratio());
        }
        items.add(item);
        for (Conversion conversion : lower) {
            ratios.add(conversion.ratio());
            items.add(conversion.item());
        }
        int[] units = new int[items.size()];
        units[units.length - 1] = 1;
        for (int i = units.length - 2; i >= 0; i--) {
            units[i] = units[i + 1] * ratios.get(i);
        }
        List<ItemKey> keys = new ArrayList<>(items.size());
        for (Item tier : items) {
            keys.add(ItemKey.of(new ItemStack(tier)));
        }
        return new Chain(Collections.unmodifiableList(keys), units);
    }

    private static boolean contains(List<Conversion> conversions, Item item) {
        return conversions.stream().anyMatch(c -> c.item() == item);
    }

    /** {@code item} × 9 (3×3) or × 4 (2×2) crafts one of the returned item. */
    private static Optional<Conversion> compress(Level level, Item item) {
        for (int side = 3; side >= 2; side--) {
            int count = side * side;
            Optional<ItemStack> result = craft(level, side, item);
            if (result.isEmpty() || result.get().getCount() != 1 || !result.get().getComponentsPatch().isEmpty() || result.get().is(item)) {
                continue;
            }
            Item compressed = result.get().getItem();
            Optional<ItemStack> back = craft(level, 1, compressed);
            boolean reversible = back.isPresent() && back.get().is(item) && back.get().getCount() == count;
            if (reversible || isOneWay(item, compressed)) {
                return Optional.of(new Conversion(compressed, count));
            }
        }
        return Optional.empty();
    }

    /** One {@code item} crafts 9 or 4 of the returned item, which crafts back into it. */
    private static Optional<Conversion> decompress(Level level, Item item) {
        Optional<ItemStack> result = craft(level, 1, item);
        if (result.isPresent()) {
            ItemStack smaller = result.get();
            int count = smaller.getCount();
            if ((count == 9 || count == 4) && smaller.getComponentsPatch().isEmpty() && !smaller.is(item)) {
                int side = count == 9 ? 3 : 2;
                Optional<ItemStack> back = craft(level, side, smaller.getItem());
                if (back.isPresent() && back.get().is(item) && back.get().getCount() == 1) {
                    return Optional.of(new Conversion(smaller.getItem(), count));
                }
            }
        }
        // One-way pairs: the compressed item has no recipe back, so look the base up and check the forward recipe.
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        for (Map.Entry<ResourceLocation, ResourceLocation> pair : ONE_WAY.entrySet()) {
            if (pair.getValue().equals(id)) {
                Item base = BuiltInRegistries.ITEM.get(pair.getKey());
                Optional<Conversion> up = compress(level, base);
                if (up.isPresent() && up.get().item() == item) {
                    return Optional.of(new Conversion(base, up.get().ratio()));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isOneWay(Item base, Item compressed) {
        ResourceLocation target = ONE_WAY.get(BuiltInRegistries.ITEM.getKey(base));
        return target != null && target.equals(BuiltInRegistries.ITEM.getKey(compressed));
    }

    /** The result of a full {@code side}×{@code side} grid of {@code item}, if any recipe matches. */
    private static Optional<ItemStack> craft(Level level, int side, Item item) {
        List<ItemStack> grid = new ArrayList<>(side * side);
        for (int i = 0; i < side * side; i++) {
            grid.add(new ItemStack(item));
        }
        CraftingInput input = CraftingInput.of(side, side, grid);
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isEmpty()) {
            return Optional.empty();
        }
        ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private record Conversion(Item item, int ratio) {
    }
}
