package com.nightbeam.omnivault.autocraft;

import com.nightbeam.omnivault.autocraft.block.AssemblerBlockEntity;
import com.nightbeam.omnivault.autocraft.block.CraftingProcessorBlockEntity;
import com.nightbeam.omnivault.autocraft.block.PatternProviderBlockEntity;
import com.nightbeam.omnivault.autocraft.job.CraftingJob;
import com.nightbeam.omnivault.autocraft.pattern.ResolvedPattern;
import com.nightbeam.omnivault.autocraft.plan.CraftingPlan;
import com.nightbeam.omnivault.autocraft.plan.PlanError;
import com.nightbeam.omnivault.autocraft.plan.Planner;
import com.nightbeam.omnivault.network.NetworkStorage;
import com.nightbeam.omnivault.network.StorageNetwork;
import com.nightbeam.omnivault.network.api.NetworkNode;
import com.nightbeam.omnivault.storage.ItemKey;
import com.nightbeam.omnivault.util.Formatting;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Autocrafting for one network: knows its pattern providers, assemblers and crafting processors,
 * plans requests, starts jobs on free processors, drives them every network tick, and catches the
 * items machines send back before they reach storage.
 */
public final class CraftingManager implements NetworkStorage.InsertInterceptor {
    private static final Object KEY = new Object();
    private static final int RESCAN_INTERVAL = 20;
    private static long devicesVersion;

    private final StorageNetwork network;
    private final List<PatternProviderBlockEntity> providers = new ArrayList<>();
    private final List<AssemblerBlockEntity> assemblers = new ArrayList<>();
    private final List<CraftingProcessorBlockEntity> processors = new ArrayList<>();
    private Map<ItemKey, List<ResolvedPattern>> byOutput = Map.of();
    private Map<ResolvedPattern, List<PatternProviderBlockEntity>> holders = Map.of();
    private Set<ItemKey> craftables = Set.of();
    private long seenVersion = -1;
    private int seenNodes = -1;
    private long lastScan = Long.MIN_VALUE;

    private CraftingManager(StorageNetwork network) {
        this.network = network;
        network.rawStorage().addInterceptor(this);
    }

    public static CraftingManager of(StorageNetwork network) {
        return network.attachment(KEY, CraftingManager::new);
    }

    /** A provider's patterns changed, or an autocrafting device joined or left a network. */
    public static void devicesChanged() {
        devicesVersion++;
    }

    // -- device discovery --------------------------------------------------------------------------

    private void scan() {
        long now = network.level().getGameTime();
        if (seenVersion == devicesVersion && seenNodes == network.nodeCount() && now - lastScan < RESCAN_INTERVAL && now >= lastScan) {
            return;
        }
        seenVersion = devicesVersion;
        seenNodes = network.nodeCount();
        lastScan = now;
        providers.clear();
        assemblers.clear();
        processors.clear();
        for (NetworkNode node : network.nodes()) {
            if (node instanceof BlockEntity be && be.isRemoved()) {
                continue;
            }
            if (node instanceof PatternProviderBlockEntity provider) {
                providers.add(provider);
            } else if (node instanceof AssemblerBlockEntity assembler) {
                assemblers.add(assembler);
            } else if (node instanceof CraftingProcessorBlockEntity processor) {
                processors.add(processor);
            }
        }
        Map<ItemKey, List<ResolvedPattern>> outputs = new LinkedHashMap<>();
        Map<ResolvedPattern, List<PatternProviderBlockEntity>> owners = new HashMap<>();
        for (PatternProviderBlockEntity provider : providers) {
            for (ResolvedPattern pattern : provider.resolvedPatterns()) {
                List<PatternProviderBlockEntity> owning = owners.computeIfAbsent(pattern, p -> new ArrayList<>());
                owning.add(provider);
                if (owning.size() > 1) {
                    continue; // the same pattern in two providers is one recipe (run by either)
                }
                for (ResolvedPattern.KeyAmount output : pattern.outputs()) {
                    outputs.computeIfAbsent(output.key(), k -> new ArrayList<>()).add(pattern);
                }
            }
        }
        byOutput = outputs;
        holders = owners;
        craftables = Collections.unmodifiableSet(new LinkedHashSet<>(outputs.keySet()));
    }

    /** Every item some pattern on this network makes. */
    public Set<ItemKey> craftables() {
        if (!network.isActive()) {
            return Set.of();
        }
        scan();
        return craftables;
    }

    public List<ResolvedPattern> patternsFor(ItemKey key) {
        return byOutput.getOrDefault(key, List.of());
    }

    public List<CraftingProcessorBlockEntity> processors() {
        scan();
        return Collections.unmodifiableList(processors);
    }

    public List<AssemblerBlockEntity> assemblers() {
        scan();
        return Collections.unmodifiableList(assemblers);
    }

    public int freeProcessors() {
        int free = 0;
        for (CraftingProcessorBlockEntity processor : processors()) {
            if (!processor.isRemoved() && !processor.isBusy()) {
                free++;
            }
        }
        return free;
    }

    // -- planning and starting ---------------------------------------------------------------------

    public CraftingPlan plan(ItemKey target, long amount) {
        if (!network.isActive()) {
            return CraftingPlan.failed(target, amount, PlanError.OFFLINE);
        }
        scan();
        CraftingPlan plan = new Planner(this::patternsFor, key -> network.storage().getCount(key)).plan(target, amount);
        if (plan.error() == PlanError.NOT_CRAFTABLE || plan.error() == PlanError.BAD_AMOUNT) {
            return plan;
        }
        if (processors.isEmpty()) {
            return plan.withError(PlanError.NO_PROCESSOR);
        }
        if (freeProcessors() == 0) {
            return plan.withError(PlanError.PROCESSORS_BUSY);
        }
        return plan;
    }

    /** Whether the plan needs an Assembler but the network has none. */
    public boolean lacksAssembler(CraftingPlan plan) {
        if (!assemblers().isEmpty()) {
            return false;
        }
        for (CraftingPlan.Step step : plan.steps()) {
            if (!step.pattern().processing()) {
                return true;
            }
        }
        return false;
    }

    public record StartResult(CraftingPlan plan, @Nullable CraftingJob job) {
        public boolean started() {
            return job != null;
        }
    }

    /**
     * Plans again with current stock and, if nothing is missing, reserves the stored ingredients
     * and starts the job on a free Crafting Processor.
     */
    public StartResult start(ItemKey target, long amount, @Nullable UUID requester) {
        CraftingPlan plan = plan(target, amount);
        if (!plan.canStart()) {
            return new StartResult(plan, null);
        }
        CraftingProcessorBlockEntity processor = null;
        for (CraftingProcessorBlockEntity candidate : processors) {
            if (!candidate.isRemoved() && !candidate.isBusy()) {
                processor = candidate;
                break;
            }
        }
        if (processor == null) {
            return new StartResult(plan.withError(PlanError.PROCESSORS_BUSY), null);
        }
        Object2LongLinkedOpenHashMap<ItemKey> reserved = new Object2LongLinkedOpenHashMap<>();
        boolean complete = true;
        for (Map.Entry<ItemKey, Long> entry : plan.used().entrySet()) {
            long taken = network.storage().extract(entry.getKey(), entry.getValue(), false);
            if (taken > 0) {
                reserved.addTo(entry.getKey(), taken);
            }
            if (taken < entry.getValue()) {
                complete = false;
                break;
            }
        }
        if (!complete) {
            reserved.object2LongEntrySet().forEach(e -> network.storage().insert(e.getKey(), e.getLongValue(), false));
            return new StartResult(plan.withError(PlanError.RESERVE_FAILED), null);
        }
        CraftingJob job = CraftingJob.create(plan, reserved, requester);
        processor.setJob(job);
        return new StartResult(plan, job);
    }

    /** Stops a job and returns everything it holds. Returns false if no such job runs here. */
    public boolean cancel(UUID jobId) {
        for (CraftingProcessorBlockEntity processor : processors()) {
            CraftingJob job = processor.job();
            if (job != null && job.id().equals(jobId)) {
                job.cancel();
                returnItems(processor, job);
                return true;
            }
        }
        return false;
    }

    /** Running jobs with the processor holding each. */
    public List<CraftingProcessorBlockEntity> busyProcessors() {
        List<CraftingProcessorBlockEntity> out = new ArrayList<>();
        for (CraftingProcessorBlockEntity processor : processors()) {
            if (!processor.isRemoved() && processor.job() != null) {
                out.add(processor);
            }
        }
        return out;
    }

    // -- running -----------------------------------------------------------------------------------

    public void tick(long gameTime) {
        scan();
        for (AssemblerBlockEntity assembler : assemblers) {
            if (!assembler.isRemoved()) {
                assembler.cycle(gameTime);
            }
        }
        for (PatternProviderBlockEntity provider : providers) {
            if (!provider.isRemoved()) {
                provider.flushOverflow();
            }
        }
        for (CraftingProcessorBlockEntity processor : List.copyOf(processors)) {
            CraftingJob job = processor.job();
            if (job == null || processor.isRemoved()) {
                continue;
            }
            if (!job.returning()) {
                advance(processor, job, gameTime);
                if (job.stepsFinished()) {
                    job.beginReturning();
                    notifyFinished(job);
                }
            }
            if (job.returning()) {
                returnItems(processor, job);
            }
        }
    }

    private void advance(CraftingProcessorBlockEntity processor, CraftingJob job, long gameTime) {
        boolean progressed = false;
        CraftingJob.Status problem = null;
        for (CraftingJob.Step step : job.steps()) {
            if (step.remaining() <= 0) {
                continue;
            }
            long runs = Math.min(job.runsAvailable(step), step.remaining());
            if (runs <= 0) {
                continue;
            }
            if (step.pattern().processing()) {
                List<PatternProviderBlockEntity> owners = holders.getOrDefault(step.pattern(), List.of());
                long pushed = 0;
                for (PatternProviderBlockEntity provider : owners) {
                    if (pushed >= runs) {
                        break;
                    }
                    if (!provider.isRemoved()) {
                        pushed += provider.push(job, step, runs - pushed);
                    }
                }
                if (pushed > 0) {
                    progressed = true;
                } else {
                    problem = worse(problem, owners.isEmpty() ? CraftingJob.Status.NO_PROVIDER : CraftingJob.Status.MACHINE_BLOCKED);
                }
            } else {
                if (assemblers.isEmpty()) {
                    problem = worse(problem, CraftingJob.Status.NO_ASSEMBLER);
                    continue;
                }
                if (!holders.containsKey(step.pattern())) {
                    problem = worse(problem, CraftingJob.Status.NO_PROVIDER);
                    continue;
                }
                long crafted = 0;
                for (AssemblerBlockEntity assembler : assemblers) {
                    if (crafted >= runs) {
                        break;
                    }
                    if (assembler.isRemoved()) {
                        continue;
                    }
                    long made = assembler.craft(job, step, runs - crafted, network.level(), gameTime);
                    if (made < 0) {
                        problem = worse(problem, CraftingJob.Status.INVALID_PATTERN);
                        break;
                    }
                    crafted += made;
                }
                if (crafted > 0) {
                    progressed = true;
                }
            }
        }
        if (progressed) {
            processor.setChanged();
            job.setStatus(CraftingJob.Status.CRAFTING);
        } else if (problem != null) {
            job.setStatus(problem);
        } else {
            boolean waiting = false;
            for (CraftingJob.Step step : job.steps()) {
                waiting |= step.inFlight() > 0;
            }
            job.setStatus(waiting ? CraftingJob.Status.WAITING : CraftingJob.Status.CRAFTING);
        }
    }

    private static CraftingJob.Status worse(@Nullable CraftingJob.Status current, CraftingJob.Status next) {
        return current == null || next.ordinal() > current.ordinal() ? next : current;
    }

    /** Puts what the job holds into storage; the processor frees up once it is all back. */
    private void returnItems(CraftingProcessorBlockEntity processor, CraftingJob job) {
        if (network.isActive()) {
            for (ResolvedPattern.KeyAmount held : job.heldSnapshot()) {
                long stored = network.storage().insert(held.key(), held.amount(), false);
                job.remove(held.key(), stored);
            }
        }
        processor.setChanged();
        if (job.buffer().isEmpty()) {
            processor.setJob(null);
        }
    }

    private void notifyFinished(CraftingJob job) {
        if (job.requester() == null || job.cancelled()) {
            return;
        }
        ServerPlayer player = network.level().getServer().getPlayerList().getPlayer(job.requester());
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.omnivault.craft.done", Formatting.full(job.amount()),
                    job.target().view().getHoverName()).withStyle(ChatFormatting.AQUA), true);
        }
    }

    // -- catching machine outputs ------------------------------------------------------------------

    @Override
    public long intercept(ItemKey key, long amount, boolean simulate) {
        long taken = 0;
        for (int i = 0; i < processors.size() && taken < amount; i++) {
            CraftingProcessorBlockEntity processor = processors.get(i);
            CraftingJob job = processor.job();
            if (job == null || processor.isRemoved()) {
                continue;
            }
            long now = job.receive(key, amount - taken, simulate);
            if (now > 0) {
                taken += now;
                if (!simulate) {
                    processor.setChanged();
                }
            }
        }
        return taken;
    }
}
