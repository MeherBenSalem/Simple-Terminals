package com.nightbeam.omnivault.neoforge.transfer;

import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Opening transactions from our simulate-flag world. */
final class Transactions {
    private Transactions() {
    }

    /**
     * A transaction of our own: nested in one already open on this thread (another mod's transfer
     * reaching through a network), so it rolls back with it, or a new root one.
     */
    @SuppressWarnings("deprecation")
    static Transaction open() {
        return Transaction.getLifecycle() == Transaction.Lifecycle.OPEN
                ? Transaction.open(Transaction.getCurrentOpenedTransaction())
                : Transaction.openRoot();
    }

    /** NeoForge forbids transactions while one is closing; storage can't be changed then. */
    static boolean closing() {
        Transaction.Lifecycle lifecycle = Transaction.getLifecycle();
        return lifecycle == Transaction.Lifecycle.CLOSING || lifecycle == Transaction.Lifecycle.ROOT_CLOSING;
    }

    static int clamp(long amount) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, amount));
    }
}
