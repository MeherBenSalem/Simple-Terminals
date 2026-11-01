package com.nightbeam.omnivault.autocraft;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.autocraft.client.AutocraftClientHandlers;
import com.nightbeam.omnivault.autocraft.job.CraftingJob;
import com.nightbeam.omnivault.autocraft.menu.CraftingStatusMenu;
import com.nightbeam.omnivault.autocraft.menu.PatternTerminalMenu;
import com.nightbeam.omnivault.autocraft.pattern.PatternData;
import com.nightbeam.omnivault.autocraft.pattern.ResolvedPattern;
import com.nightbeam.omnivault.autocraft.plan.CraftingPlan;
import com.nightbeam.omnivault.packet.PacketCodec;
import com.nightbeam.omnivault.packet.PacketCodecs;
import com.nightbeam.omnivault.packet.Payload;
import com.nightbeam.omnivault.storage.ItemKey;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Payloads for the craft request, crafting status and pattern encoding screens. */
public final class AutocraftPackets {
    private AutocraftPackets() {
    }

    public record Amount(ItemKey key, long amount) {
        public static final PacketCodec<Amount> CODEC = PacketCodec.composite(
                ItemKey.STREAM_CODEC, Amount::key,
                PacketCodecs.VAR_LONG, Amount::amount,
                Amount::new);
    }

    public record PlanEntry(ItemKey key, long used, long crafted, long missing) {
        public static final PacketCodec<PlanEntry> CODEC = PacketCodec.composite(
                ItemKey.STREAM_CODEC, PlanEntry::key,
                PacketCodecs.VAR_LONG, PlanEntry::used,
                PacketCodecs.VAR_LONG, PlanEntry::crafted,
                PacketCodecs.VAR_LONG, PlanEntry::missing,
                PlanEntry::new);
    }

    /**
     * What the request screen shows about a plan.
     *
     * @param error {@link com.nightbeam.omnivault.autocraft.plan.PlanError} ordinal, or -1
     */
    public record PlanView(long amount, int error, int steps, long crafts, boolean noAssembler, int freeProcessors,
                           int processors, List<PlanEntry> entries) {
        public static final PacketCodec<PlanView> CODEC = PacketCodec.of(
                (buf, v) -> {
                    buf.writeVarLong(v.amount);
                    buf.writeVarInt(v.error);
                    buf.writeVarInt(v.steps);
                    buf.writeVarLong(v.crafts);
                    buf.writeBoolean(v.noAssembler);
                    buf.writeVarInt(v.freeProcessors);
                    buf.writeVarInt(v.processors);
                    PlanEntry.CODEC.apply(PacketCodecs.list()).encode(buf, v.entries);
                },
                buf -> new PlanView(buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readBoolean(),
                        buf.readVarInt(), buf.readVarInt(), PlanEntry.CODEC.apply(PacketCodecs.list()).decode(buf)));

        public static PlanView of(CraftingPlan plan, CraftingManager manager) {
            List<PlanEntry> entries = new ArrayList<>();
            for (CraftingPlan.Entry entry : plan.entries()) {
                entries.add(new PlanEntry(entry.key(), entry.used(), entry.crafted(), entry.missing()));
            }
            return new PlanView(plan.amount(), plan.error() == null ? -1 : plan.error().ordinal(), plan.steps().size(),
                    plan.totalCrafts(), manager.lacksAssembler(plan), manager.freeProcessors(), manager.processors().size(), entries);
        }

        public boolean canStart() {
            return error < 0 && steps > 0;
        }
    }

    /** One job row on the crafting status screen. */
    public record JobView(UUID id, ItemKey target, long amount, long done, long total, int status, List<Amount> awaiting) {
        public static final PacketCodec<JobView> CODEC = PacketCodec.of(
                (buf, v) -> {
                    PacketCodecs.UUID.encode(buf, v.id);
                    ItemKey.STREAM_CODEC.encode(buf, v.target);
                    buf.writeVarLong(v.amount);
                    buf.writeVarLong(v.done);
                    buf.writeVarLong(v.total);
                    buf.writeVarInt(v.status);
                    Amount.CODEC.apply(PacketCodecs.list()).encode(buf, v.awaiting);
                },
                buf -> new JobView(PacketCodecs.UUID.decode(buf), ItemKey.STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong(),
                        buf.readVarLong(), buf.readVarInt(), Amount.CODEC.apply(PacketCodecs.list()).decode(buf)));

        public static JobView of(CraftingJob job) {
            List<Amount> awaiting = new ArrayList<>();
            for (ResolvedPattern.KeyAmount entry : job.awaitingSummary(4)) {
                awaiting.add(new Amount(entry.key(), entry.amount()));
            }
            return new JobView(job.id(), job.target(), job.amount(), job.completedRuns(), job.totalRuns(), job.status().ordinal(), awaiting);
        }

        public CraftingJob.Status statusValue() {
            return CraftingJob.Status.values()[Math.floorMod(status, CraftingJob.Status.values().length)];
        }
    }

    /** Server → client: the plan for the open request screen. */
    public record PlanResult(int containerId, PlanView plan) implements Payload {
        public static final Type<PlanResult> TYPE = new Type<>(OmniVault.id("craft_plan"));
        public static final PacketCodec<PlanResult> CODEC = PacketCodec.composite(
                PacketCodecs.VAR_INT, PlanResult::containerId,
                PlanView.CODEC, PlanResult::plan,
                PlanResult::new);

        @Override
        public Type<? extends Payload> type() {
            return TYPE;
        }
    }

    /** Server → client: the jobs on the network, for the status screen. */
    public record JobList(int containerId, int processors, List<JobView> jobs) implements Payload {
        public static final Type<JobList> TYPE = new Type<>(OmniVault.id("craft_jobs"));
        public static final PacketCodec<JobList> CODEC = PacketCodec.composite(
                PacketCodecs.VAR_INT, JobList::containerId,
                PacketCodecs.VAR_INT, JobList::processors,
                JobView.CODEC.apply(PacketCodecs.list()), JobList::jobs,
                JobList::new);

        @Override
        public Type<? extends Payload> type() {
            return TYPE;
        }
    }

    /** Client → server: cancel a job from the status screen. */
    public record CancelJob(int containerId, UUID job) implements Payload {
        public static final Type<CancelJob> TYPE = new Type<>(OmniVault.id("cancel_craft"));
        public static final PacketCodec<CancelJob> CODEC = PacketCodec.composite(
                PacketCodecs.VAR_INT, CancelJob::containerId,
                PacketCodecs.UUID, CancelJob::job,
                CancelJob::new);

        @Override
        public Type<? extends Payload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: load a recipe from a recipe viewer into an open Pattern Encoding Terminal.
     * Crafting recipes fill the 3×3 grid; anything else becomes a processing recipe.
     */
    public record PatternTransfer(int containerId, boolean processing, List<ItemStack> inputs, List<ItemStack> outputs)
            implements Payload {
        public static final Type<PatternTransfer> TYPE = new Type<>(OmniVault.id("pattern_transfer"));
        public static final PacketCodec<PatternTransfer> CODEC = PacketCodec.composite(
                PacketCodecs.VAR_INT, PatternTransfer::containerId,
                PacketCodecs.BOOL, PatternTransfer::processing,
                PacketCodecs.ITEM_STACK.apply(PacketCodecs.list(PatternData.MAX_INPUTS)), PatternTransfer::inputs,
                PacketCodecs.ITEM_STACK.apply(PacketCodecs.list(PatternData.MAX_OUTPUTS)), PatternTransfer::outputs,
                PatternTransfer::new);

        @Override
        public Type<? extends Payload> type() {
            return TYPE;
        }
    }

    public static void init() {
        OmniVaultCommon.packets(registrar -> {
            registrar.toClient(PlanResult.TYPE, PlanResult.CODEC, payload -> AutocraftClientHandlers.plan(payload));
            registrar.toClient(JobList.TYPE, JobList.CODEC, payload -> AutocraftClientHandlers.jobs(payload));
            registrar.toServer(CancelJob.TYPE, CancelJob.CODEC, (payload, player) -> {
                if (player.containerMenu instanceof CraftingStatusMenu menu && menu.containerId == payload.containerId()
                        && menu.stillValid(player)) {
                    menu.cancel(payload.job());
                }
            });
            registrar.toServer(PatternTransfer.TYPE, PatternTransfer.CODEC, (payload, player) -> {
                if (player.containerMenu instanceof PatternTerminalMenu menu && menu.containerId == payload.containerId()
                        && menu.stillValid(player)) {
                    menu.transfer(payload.processing(), payload.inputs(), payload.outputs());
                }
            });
        });
    }
}
