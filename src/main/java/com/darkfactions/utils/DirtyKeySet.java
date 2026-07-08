package com.darkfactions.utils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pure, dependency-free set of keys pending an upsert to storage.
 *
 * <p>Mirrors {@link ClaimChangeSet}'s drain idiom for the simpler case of a
 * single upsert-only table: recording a key is idempotent, and draining takes
 * an immutable snapshot and clears the set so a save cycle only writes the
 * keys that changed since the last flush instead of the whole table. All
 * methods are synchronized so the main thread can record changes while a save
 * task drains them. Free of any Bukkit/Paper types so it can be unit tested
 * directly.
 */
public final class DirtyKeySet<T> {

    private final Set<T> keys = new LinkedHashSet<>();

    /** Mark {@code key} as needing to be written. */
    public synchronized void markDirty(T key) {
        keys.add(key);
    }

    /** Drop {@code key} without writing it, e.g. when it was deleted before ever being flushed. */
    public synchronized void clear(T key) {
        keys.remove(key);
    }

    /** True when there is nothing to flush. */
    public synchronized boolean isEmpty() {
        return keys.isEmpty();
    }

    /**
     * Take and clear all pending keys as an immutable snapshot. After this
     * call the set is empty; further markDirty calls accumulate for the next flush.
     */
    public synchronized Set<T> drain() {
        Set<T> snapshot = Set.copyOf(keys);
        keys.clear();
        return snapshot;
    }

    /** Re-add keys after a failed flush so they're retried on the next save cycle. */
    public synchronized void restore(Set<T> failedKeys) {
        keys.addAll(failedKeys);
    }
}
