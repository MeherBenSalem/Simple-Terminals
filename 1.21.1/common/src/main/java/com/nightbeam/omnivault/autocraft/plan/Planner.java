package com.nightbeam.omnivault.autocraft.plan;

import com.nightbeam.omnivault.autocraft.pattern.ResolvedPattern;
import com.nightbeam.omnivault.storage.ItemKey;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Works out how to make N of an item. The requested item is always crafted; every ingredient is
 * taken from leftovers of earlier steps first, then from storage, and only the shortfall is
 * crafted, recursively. Each step is batched (one entry per pattern with a run count). When several
 * patterns make the same item the first one whose whole sub-tree can be satisfied wins; if none
 * can, the first is kept so its missing ingredients are reported. Cycles (an item needed to make
 * itself) and runaway depth count as missing instead of recursing forever.
 */
public final class Planner {
    public static final int MAX_DEPTH = 32;
    public static final long MAX_AMOUNT = 1_000_000_000L;
    private static final int MAX_WORK = 100_000;

    private final Function<ItemKey, List<ResolvedPattern>> patterns;
    private final ToLongFunction<ItemKey> stock;
    private int work;

    /**
     * @param patterns the patterns that output an item, in priority order
     * @param stock    how many of an item storage holds right now
     */
    public Planner(Function<ItemKey, List<ResolvedPattern>> patterns, ToLongFunction<ItemKey> stock) {
        this.patterns = patterns;
        this.stock = stock;
    }

    public CraftingPlan plan(ItemKey target, long amount) {
        if (amount <= 0 || amount > MAX_AMOUNT) {
            return CraftingPlan.failed(target, amount, PlanError.BAD_AMOUNT);
        }
        if (patterns.apply(target).isEmpty()) {
            return CraftingPlan.failed(target, amount, PlanError.NOT_CRAFTABLE);
        }
        State state = new State();
        work = 0;
        try {
            request(state, target, amount, new ArrayDeque<>(), false);
        } catch (TooComplex | ArithmeticException e) {
            return CraftingPlan.failed(target, amount, PlanError.TOO_COMPLEX);
        }
        List<CraftingPlan.Step> steps = new ArrayList<>();
        state.steps.forEach((pattern, crafts) -> steps.add(new CraftingPlan.Step(pattern, crafts)));
        CraftingPlan plan = new CraftingPlan(target, amount, List.copyOf(steps), freeze(state.usedStock), freeze(state.crafted),
                freeze(state.missing), null);
        return plan.missing().isEmpty() ? plan : plan.withError(PlanError.MISSING);
    }

    private static Map<ItemKey, Long> freeze(Object2LongLinkedOpenHashMap<ItemKey> map) {
        Map<ItemKey, Long> out = new LinkedHashMap<>();
        map.object2LongEntrySet().forEach(e -> {
            if (e.getLongValue() > 0) {
                out.put(e.getKey(), e.getLongValue());
            }
        });
        return out;
    }

    /** Makes {@code amount} of {@code key} available; returns false if anything had to be marked missing. */
    private boolean request(State state, ItemKey key, long amount, Deque<ItemKey> path, boolean allowStock) {
        if (++work > MAX_WORK) {
            throw new TooComplex();
        }
        long need = amount;
        long leftover = Math.min(state.surplus.getLong(key), need);
        if (leftover > 0) {
            state.surplus.addTo(key, -leftover);
            need -= leftover;
        }
        if (allowStock && need > 0) {
            long available = stock.applyAsLong(key) - state.usedStock.getLong(key);
            long take = Math.min(Math.max(0, available), need);
            if (take > 0) {
                state.usedStock.addTo(key, take);
                need -= take;
            }
        }
        if (need == 0) {
            return true;
        }
        List<ResolvedPattern> candidates = patterns.apply(key);
        if (candidates.isEmpty() || path.contains(key) || path.size() >= MAX_DEPTH) {
            state.missing.addTo(key, need);
            return false;
        }
        if (candidates.size() > 1) {
            for (ResolvedPattern candidate : candidates) {
                State trial = state.copy();
                if (expand(trial, candidate, key, need, path)) {
                    state.set(trial);
                    return true;
                }
            }
        }
        return expand(state, candidates.get(0), key, need, path);
    }

    private boolean expand(State state, ResolvedPattern pattern, ItemKey key, long need, Deque<ItemKey> path) {
        long perRun = pattern.outputAmount(key);
        long crafts = (need + perRun - 1) / perRun;
        boolean ok = true;
        path.push(key);
        for (ResolvedPattern.KeyAmount input : pattern.inputs()) {
            ok &= request(state, input.key(), Math.multiplyExact(input.amount(), crafts), path, true);
        }
        path.pop();
        for (ResolvedPattern.KeyAmount output : pattern.outputs()) {
            long made = Math.multiplyExact(output.amount(), crafts);
            state.crafted.addTo(output.key(), made);
            state.surplus.addTo(output.key(), made);
        }
        state.surplus.addTo(key, -need);
        state.steps.merge(pattern, crafts, Long::sum);
        return ok;
    }

    private static final class State {
        Object2LongLinkedOpenHashMap<ItemKey> usedStock = new Object2LongLinkedOpenHashMap<>();
        Object2LongLinkedOpenHashMap<ItemKey> surplus = new Object2LongLinkedOpenHashMap<>();
        Object2LongLinkedOpenHashMap<ItemKey> crafted = new Object2LongLinkedOpenHashMap<>();
        Object2LongLinkedOpenHashMap<ItemKey> missing = new Object2LongLinkedOpenHashMap<>();
        LinkedHashMap<ResolvedPattern, Long> steps = new LinkedHashMap<>();

        State copy() {
            State copy = new State();
            copy.usedStock = usedStock.clone();
            copy.surplus = surplus.clone();
            copy.crafted = crafted.clone();
            copy.missing = missing.clone();
            copy.steps = new LinkedHashMap<>(steps);
            return copy;
        }

        void set(State other) {
            usedStock = other.usedStock;
            surplus = other.surplus;
            crafted = other.crafted;
            missing = other.missing;
            steps = other.steps;
        }
    }

    private static final class TooComplex extends RuntimeException {
        TooComplex() {
            super(null, null, false, false);
        }
    }
}
