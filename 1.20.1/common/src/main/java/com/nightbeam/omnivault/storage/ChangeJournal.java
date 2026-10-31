package com.nightbeam.omnivault.storage;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lets a loader transaction (Fabric) take back network changes. While a journal records, networks
 * note how to undo each change to our own storage (cells, our blocks behind a Storage Bus), and leave
 * items for waiting crafting jobs in storage until the transaction commits. Storage that joins the
 * transaction itself ({@link ItemStorage#joinsTransactions()}, another mod's storage) isn't noted: it
 * rolls back on its own. Undo steps and commits never reach such storage, since loaders forbid
 * transactions while one is closing.
 */
public final class ChangeJournal {
    private static final ThreadLocal<ChangeJournal> CURRENT = new ThreadLocal<>();

    private final List<Runnable> undo = new ArrayList<>();
    private final List<Runnable> onCommit = new ArrayList<>();
    /** How many network operations are running inside each other; only the outermost one records. */
    private int depth;

    /** The journal recording on this thread, or null. */
    @Nullable
    public static ChangeJournal current() {
        return CURRENT.get();
    }

    /** Runs {@code action} with this journal recording. */
    public <T> T record(Supplier<T> action) {
        ChangeJournal previous = CURRENT.get();
        CURRENT.set(this);
        try {
            return action.get();
        } finally {
            CURRENT.set(previous);
        }
    }

    /**
     * Brackets one network operation. Returns true for the outermost one, which is the one that
     * records: a network reached through another network's port is undone as a whole by the outer one.
     */
    public boolean enter() {
        return depth++ == 0;
    }

    public void exit() {
        depth--;
    }

    public void addUndo(Runnable step) {
        undo.add(step);
    }

    /** Work that may only happen once the change is final, such as handing items to a crafting job. */
    public void addOnCommit(Runnable step) {
        onCommit.add(step);
    }

    /** A mark to roll back to: how many undo and commit steps exist now. */
    public long mark() {
        return ((long) undo.size() << 32) | onCommit.size();
    }

    /** Undoes everything recorded after {@code mark}, newest first, and drops later commit steps. */
    public void rollBackTo(long mark) {
        int undoSize = (int) (mark >>> 32);
        int commitSize = (int) mark;
        while (undo.size() > undoSize) {
            undo.remove(undo.size() - 1).run();
        }
        while (onCommit.size() > commitSize) {
            onCommit.remove(onCommit.size() - 1);
        }
    }

    /** The changes are final: forget the undo steps and run the commit steps. */
    public void commit() {
        undo.clear();
        List<Runnable> steps = List.copyOf(onCommit);
        onCommit.clear();
        steps.forEach(Runnable::run);
    }
}
