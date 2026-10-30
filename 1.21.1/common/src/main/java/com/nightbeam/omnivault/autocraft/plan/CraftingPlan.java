package com.nightbeam.omnivault.autocraft.plan;

import com.nightbeam.omnivault.autocraft.pattern.ResolvedPattern;
import com.nightbeam.omnivault.storage.ItemKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The outcome of planning "craft N × item": the pattern runs in dependency order, what is taken
 * from storage, what gets crafted and what is missing.
 *
 * @param steps   pattern runs, ingredients before the things made from them
 * @param used    items taken from network storage (reserved when the job starts)
 * @param crafted items made by the steps, byproducts included
 * @param missing items neither stored nor craftable
 * @param error   why the plan cannot run at all, or null
 */
public record CraftingPlan(ItemKey target, long amount, List<Step> steps, Map<ItemKey, Long> used,
                           Map<ItemKey, Long> crafted, Map<ItemKey, Long> missing, @Nullable PlanError error) {
    public record Step(ResolvedPattern pattern, long crafts) {
    }

    public static CraftingPlan failed(ItemKey target, long amount, PlanError error) {
        return new CraftingPlan(target, amount, List.of(), Map.of(), Map.of(), Map.of(), error);
    }

    public CraftingPlan withError(@Nullable PlanError error) {
        return new CraftingPlan(target, amount, steps, used, crafted, missing, error);
    }

    public boolean canStart() {
        return error == null && missing.isEmpty() && !steps.isEmpty();
    }

    public long used(ItemKey key) {
        return used.getOrDefault(key, 0L);
    }

    public long crafted(ItemKey key) {
        return crafted.getOrDefault(key, 0L);
    }

    public long missing(ItemKey key) {
        return missing.getOrDefault(key, 0L);
    }

    public long totalCrafts() {
        long total = 0;
        for (Step step : steps) {
            total += step.crafts();
        }
        return total;
    }

    /** Every item the plan touches with its three amounts: missing first, then crafted, then stored. */
    public List<Entry> entries() {
        Set<ItemKey> keys = new LinkedHashSet<>();
        keys.add(target);
        keys.addAll(missing.keySet());
        keys.addAll(crafted.keySet());
        keys.addAll(used.keySet());
        List<Entry> out = new ArrayList<>();
        for (ItemKey key : keys) {
            Entry entry = new Entry(key, used(key), crafted(key), missing(key));
            if (entry.used() > 0 || entry.crafted() > 0 || entry.missing() > 0) {
                out.add(entry);
            }
        }
        out.sort(Comparator.comparing((Entry e) -> !e.key().equals(target))
                .thenComparing(e -> e.missing() <= 0)
                .thenComparing(e -> e.crafted() <= 0));
        return out;
    }

    public record Entry(ItemKey key, long used, long crafted, long missing) {
    }
}
