package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class DirtyKeySetTest {

    @Test
    void startsEmpty() {
        assertTrue(new DirtyKeySet<String>().isEmpty());
    }

    @Test
    void markDirtyThenDrainReturnsTheKey() {
        DirtyKeySet<String> set = new DirtyKeySet<>();
        set.markDirty("a");
        assertEquals(Set.of("a"), set.drain());
    }

    @Test
    void duplicateMarksCollapse() {
        DirtyKeySet<String> set = new DirtyKeySet<>();
        set.markDirty("a");
        set.markDirty("a");
        assertEquals(Set.of("a"), set.drain());
    }

    @Test
    void drainClearsState() {
        DirtyKeySet<String> set = new DirtyKeySet<>();
        set.markDirty("a");
        set.drain();
        assertTrue(set.isEmpty());
        assertTrue(set.drain().isEmpty());
    }

    @Test
    void clearDropsAKeyBeforeItsEverFlushed() {
        DirtyKeySet<String> set = new DirtyKeySet<>();
        set.markDirty("a");
        set.markDirty("b");
        set.clear("a");
        assertEquals(Set.of("b"), set.drain());
    }

    @Test
    void restoreReAddsKeysAfterAFailedFlush() {
        DirtyKeySet<String> set = new DirtyKeySet<>();
        set.markDirty("a");
        Set<String> drained = set.drain();
        assertTrue(set.isEmpty());

        set.restore(drained);
        assertEquals(Set.of("a"), set.drain());
    }

    @Test
    void recordingAfterDrainAccumulatesForNextFlush() {
        DirtyKeySet<String> set = new DirtyKeySet<>();
        set.markDirty("a");
        set.drain();
        set.markDirty("b");
        assertEquals(Set.of("b"), set.drain());
    }

    @Test
    void drainSnapshotIsImmutable() {
        DirtyKeySet<String> set = new DirtyKeySet<>();
        set.markDirty("a");
        Set<String> drained = set.drain();
        assertThrows(UnsupportedOperationException.class, () -> drained.add("x"));
    }
}
