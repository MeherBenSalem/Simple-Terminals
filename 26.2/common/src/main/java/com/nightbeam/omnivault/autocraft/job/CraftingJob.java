package com.nightbeam.omnivault.autocraft.job;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.autocraft.pattern.PatternData;
import com.nightbeam.omnivault.autocraft.pattern.ResolvedPattern;
import com.nightbeam.omnivault.autocraft.plan.CraftingPlan;
import com.nightbeam.omnivault.storage.ItemKey;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A running craft request. It owns every item it works with: the stored ingredients reserved when
 * it started, intermediate products and remainders. Processing steps wait for their outputs to come
 * back into the network. When all steps are done (or the job is cancelled) the job hands everything
 * it still holds, the requested items included, back to the network.
 */
public final class CraftingJob {
    public enum Status {
        CRAFTING, WAITING, NO_ASSEMBLER, NO_PROVIDER, MACHINE_BLOCKED, INVALID_PATTERN, RETURNING
    }

    private final UUID id;
    private final ItemKey target;
    private final long amount;
    @Nullable
    private final UUID requester;
    private final List<Step> steps;
    private final Object2LongLinkedOpenHashMap<ItemKey> buffer;
    private boolean returning;
    private boolean cancelled;
    private Status status = Status.CRAFTING;

    private CraftingJob(UUID id, ItemKey target, long amount, @Nullable UUID requester, List<Step> steps,
                        Object2LongLinkedOpenHashMap<ItemKey> buffer) {
        this.id = id;
        this.target = target;
        this.amount = amount;
        this.requester = requester;
        this.steps = steps;
        this.buffer = buffer;
    }

    /** A job for {@code plan} whose reserved ingredients are already in {@code reserved}. */
    public static CraftingJob create(CraftingPlan plan, Object2LongLinkedOpenHashMap<ItemKey> reserved, @Nullable UUID requester) {
        List<Step> steps = new ArrayList<>();
        for (CraftingPlan.Step step : plan.steps()) {
            steps.add(new Step(step.pattern(), step.crafts(), step.crafts(), new Object2LongLinkedOpenHashMap<>()));
        }
        return new CraftingJob(UUID.randomUUID(), plan.target(), plan.amount(), requester, steps, reserved);
    }

    public UUID id() {
        return id;
    }

    public ItemKey target() {
        return target;
    }

    public long amount() {
        return amount;
    }

    @Nullable
    public UUID requester() {
        return requester;
    }

    public List<Step> steps() {
        return Collections.unmodifiableList(steps);
    }

    public Object2LongLinkedOpenHashMap<ItemKey> buffer() {
        return buffer;
    }

    public Status status() {
        return returning ? Status.RETURNING : status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean returning() {
        return returning;
    }

    public boolean cancelled() {
        return cancelled;
    }

    /** All steps are done and nothing is in flight: time to give the results back. */
    public boolean stepsFinished() {
        for (Step step : steps) {
            if (step.remaining > 0 || !step.awaiting.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Stops all work; whatever the job holds goes back to the network. Late machine outputs go to storage. */
    public void cancel() {
        cancelled = true;
        returning = true;
        for (Step step : steps) {
            step.remaining = 0;
            step.awaiting.clear();
        }
    }

    public void beginReturning() {
        returning = true;
    }

    // -- buffer ------------------------------------------------------------------------------------

    /** A copy of everything the job holds right now. */
    public List<ResolvedPattern.KeyAmount> heldSnapshot() {
        List<ResolvedPattern.KeyAmount> out = new ArrayList<>(buffer.size());
        buffer.object2LongEntrySet().forEach(e -> out.add(new ResolvedPattern.KeyAmount(e.getKey(), e.getLongValue())));
        return out;
    }

    public long held(ItemKey key) {
        return buffer.getLong(key);
    }

    public void add(ItemKey key, long amount) {
        if (amount > 0) {
            buffer.addTo(key, amount);
        }
    }

    public void remove(ItemKey key, long amount) {
        long left = buffer.getLong(key) - amount;
        if (left > 0) {
            buffer.put(key, left);
        } else {
            buffer.removeLong(key);
        }
    }

    /** How many runs of {@code step} the items in the buffer cover. */
    public long runsAvailable(Step step) {
        long runs = Long.MAX_VALUE;
        for (ResolvedPattern.KeyAmount input : step.pattern.inputs()) {
            runs = Math.min(runs, held(input.key()) / input.amount());
        }
        return runs == Long.MAX_VALUE ? 0 : runs;
    }

    /** Takes one run's worth of {@code step}'s inputs out of the buffer. */
    public void consumeInputs(Step step, long runs) {
        for (ResolvedPattern.KeyAmount input : step.pattern.inputs()) {
            remove(input.key(), input.amount() * runs);
        }
    }

    // -- processing outputs ------------------------------------------------------------------------

    public long awaiting(ItemKey key) {
        long total = 0;
        for (Step step : steps) {
            total += step.awaiting.getLong(key);
        }
        return total;
    }

    /** Items arriving from a machine. Returns how many this job was waiting for (and took, unless simulating). */
    public long receive(ItemKey key, long amount, boolean simulate) {
        if (returning) {
            return 0;
        }
        long taken = 0;
        for (Step step : steps) {
            if (taken >= amount) {
                break;
            }
            long waiting = step.awaiting.getLong(key);
            if (waiting <= 0) {
                continue;
            }
            long now = Math.min(waiting, amount - taken);
            taken += now;
            if (!simulate) {
                if (waiting - now > 0) {
                    step.awaiting.put(key, waiting - now);
                } else {
                    step.awaiting.removeLong(key);
                }
            }
        }
        if (!simulate) {
            add(key, taken);
        }
        return taken;
    }

    /** A processing run was handed to a machine: its outputs are now expected back. */
    public void dispatched(Step step, long runs) {
        step.remaining -= runs;
        for (ResolvedPattern.KeyAmount output : step.pattern.outputs()) {
            step.awaiting.addTo(output.key(), output.amount() * runs);
        }
    }

    // -- progress ----------------------------------------------------------------------------------

    public long totalRuns() {
        long total = 0;
        for (Step step : steps) {
            total += step.total;
        }
        return total;
    }

    public long completedRuns() {
        long done = 0;
        for (Step step : steps) {
            done += step.total - step.remaining - step.inFlight();
        }
        return done;
    }

    /** Up to {@code limit} items the job is still waiting on from machines. */
    public List<ResolvedPattern.KeyAmount> awaitingSummary(int limit) {
        List<ResolvedPattern.KeyAmount> out = new ArrayList<>();
        Object2LongLinkedOpenHashMap<ItemKey> merged = new Object2LongLinkedOpenHashMap<>();
        for (Step step : steps) {
            step.awaiting.object2LongEntrySet().forEach(e -> merged.addTo(e.getKey(), e.getLongValue()));
        }
        for (Object2LongMap.Entry<ItemKey> entry : merged.object2LongEntrySet()) {
            if (out.size() >= limit) {
                break;
            }
            out.add(new ResolvedPattern.KeyAmount(entry.getKey(), entry.getLongValue()));
        }
        return out;
    }

    // -- persistence -------------------------------------------------------------------------------

    public void save(ValueOutput output) {
        output.store("id", UUIDUtil.CODEC, id);
        output.store("target", ItemKey.CODEC, target);
        output.putLong("amount", amount);
        output.storeNullable("requester", UUIDUtil.CODEC, requester);
        output.putBoolean("returning", returning);
        output.putBoolean("cancelled", cancelled);
        ValueOutput.ValueOutputList stepList = output.childrenList("steps");
        for (Step step : steps) {
            ValueOutput saved = stepList.addChild();
            saved.store("pattern", PatternData.CODEC, step.pattern.data());
            saved.putLong("total", step.total);
            saved.putLong("remaining", step.remaining);
            saveCounts(saved.childrenList("awaiting"), step.awaiting);
        }
        saveCounts(output.childrenList("buffer"), buffer);
    }

    @Nullable
    public static CraftingJob load(ValueInput input) {
        ItemKey target = input.read("target", ItemKey.CODEC).orElse(null);
        UUID id = input.read("id", UUIDUtil.CODEC).orElse(null);
        if (target == null || id == null) {
            return null;
        }
        List<Step> steps = new ArrayList<>();
        boolean broken = false;
        for (ValueInput saved : input.childrenListOrEmpty("steps")) {
            PatternData data = saved.read("pattern", PatternData.CODEC).orElse(null);
            if (data == null || !data.isValid()) {
                broken = true;
                continue;
            }
            steps.add(new Step(new ResolvedPattern(data), saved.getLongOr("total", 0L), saved.getLongOr("remaining", 0L),
                    loadCounts(saved.childrenListOrEmpty("awaiting"))));
        }
        CraftingJob job = new CraftingJob(id, target, input.getLongOr("amount", 0L),
                input.read("requester", UUIDUtil.CODEC).orElse(null), steps, loadCounts(input.childrenListOrEmpty("buffer")));
        job.returning = input.getBooleanOr("returning", false);
        job.cancelled = input.getBooleanOr("cancelled", false);
        if (broken) {
            // A pattern no longer loads (removed mod): give back what we hold rather than stall forever.
            OmniVault.LOG.warn("Crafting job {} lost a step on load; returning its items", job.id);
            job.cancel();
        }
        return job;
    }

    private static void saveCounts(ValueOutput.ValueOutputList list, Object2LongLinkedOpenHashMap<ItemKey> counts) {
        counts.object2LongEntrySet().forEach(entry -> {
            ValueOutput saved = list.addChild();
            saved.store("item", ItemKey.CODEC, entry.getKey());
            saved.putLong("count", entry.getLongValue());
        });
    }

    private static Object2LongLinkedOpenHashMap<ItemKey> loadCounts(ValueInput.ValueInputList list) {
        Object2LongLinkedOpenHashMap<ItemKey> counts = new Object2LongLinkedOpenHashMap<>();
        for (ValueInput saved : list) {
            ItemKey key = saved.read("item", ItemKey.CODEC).orElse(null);
            long count = saved.getLongOr("count", 0L);
            if (key != null && count > 0) {
                counts.addTo(key, count);
            }
        }
        return counts;
    }

    /** One pattern in the job, run {@code total} times. */
    public static final class Step {
        private final ResolvedPattern pattern;
        private final long total;
        private long remaining;
        private final Object2LongLinkedOpenHashMap<ItemKey> awaiting;

        Step(ResolvedPattern pattern, long total, long remaining, Object2LongLinkedOpenHashMap<ItemKey> awaiting) {
            this.pattern = pattern;
            this.total = total;
            this.remaining = remaining;
            this.awaiting = awaiting;
        }

        public ResolvedPattern pattern() {
            return pattern;
        }

        public long total() {
            return total;
        }

        /** Runs not started yet. */
        public long remaining() {
            return remaining;
        }

        public void ran(long runs) {
            remaining -= runs;
        }

        /** Runs handed to a machine whose outputs have not all come back. */
        public long inFlight() {
            long runs = 0;
            for (ResolvedPattern.KeyAmount output : pattern.outputs()) {
                long waiting = awaiting.getLong(output.key());
                if (waiting > 0) {
                    runs = Math.max(runs, (waiting + output.amount() - 1) / output.amount());
                }
            }
            return runs;
        }
    }
}
