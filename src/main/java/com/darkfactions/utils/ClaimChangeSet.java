package com.darkfactions.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure, dependency-free record of pending claim persistence changes.
 *
 * <p>{@code ClaimManager} keeps its claims in memory and flushes them to the
 * {@code DataStore} asynchronously. The previous flush re-saved every in-memory
 * claim but never issued a delete, so unclaimed chunks lingered in the database
 * and reappeared on restart. This tracks which claim keys need an upsert and
 * which need a delete so the flush can apply both, and only the rows that
 * actually changed are written.
 *
 * <p>Upserts and deletes are mutually exclusive per key with last-write-wins:
 * recording one removes the key from the other. All methods are synchronized so
 * the main thread can record changes while a save task drains them. Free of any
 * Bukkit/Paper types so it can be unit tested directly.
 */
public final class ClaimChangeSet {

    private final Set<String> upserts = new LinkedHashSet<>();
    private final Set<String> deletes = new LinkedHashSet<>();

    /** Mark {@code key} as needing to be written; cancels any pending delete. */
    public synchronized void recordUpsert(String key) {
        deletes.remove(key);
        upserts.add(key);
    }

    /** Mark {@code key} as needing to be deleted; cancels any pending upsert. */
    public synchronized void recordDelete(String key) {
        upserts.remove(key);
        deletes.add(key);
    }

    /** True when there is nothing to flush. */
    public synchronized boolean isEmpty() {
        return upserts.isEmpty() && deletes.isEmpty();
    }

    /**
     * Take and clear all pending changes as an immutable snapshot. After this
     * call the change set is empty; further recordings accumulate for the next
     * flush.
     */
    public synchronized Drain drain() {
        Drain drain = new Drain(new ArrayList<>(upserts), new ArrayList<>(deletes));
        upserts.clear();
        deletes.clear();
        return drain;
    }

    /** An immutable snapshot of pending upsert and delete keys. */
    public record Drain(List<String> upserts, List<String> deletes) {
        public Drain {
            upserts = List.copyOf(upserts);
            deletes = List.copyOf(deletes);
        }
    }
}
