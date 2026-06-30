package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ClaimChangeSetTest {

    @Test
    void startsEmpty() {
        assertTrue(new ClaimChangeSet().isEmpty());
    }

    @Test
    void recordsUpsertsAndDeletes() {
        ClaimChangeSet cs = new ClaimChangeSet();
        cs.recordUpsert("w:1:1");
        cs.recordDelete("w:2:2");
        assertFalse(cs.isEmpty());

        ClaimChangeSet.Drain drain = cs.drain();
        assertEquals(List.of("w:1:1"), drain.upserts());
        assertEquals(List.of("w:2:2"), drain.deletes());
    }

    @Test
    void upsertCancelsPendingDelete() {
        ClaimChangeSet cs = new ClaimChangeSet();
        cs.recordDelete("w:1:1");
        cs.recordUpsert("w:1:1");

        ClaimChangeSet.Drain drain = cs.drain();
        assertEquals(List.of("w:1:1"), drain.upserts());
        assertTrue(drain.deletes().isEmpty());
    }

    @Test
    void deleteCancelsPendingUpsert() {
        ClaimChangeSet cs = new ClaimChangeSet();
        cs.recordUpsert("w:1:1");
        cs.recordDelete("w:1:1");

        ClaimChangeSet.Drain drain = cs.drain();
        assertEquals(List.of("w:1:1"), drain.deletes());
        assertTrue(drain.upserts().isEmpty());
    }

    @Test
    void duplicateUpsertsCollapse() {
        ClaimChangeSet cs = new ClaimChangeSet();
        cs.recordUpsert("w:1:1");
        cs.recordUpsert("w:1:1");

        assertEquals(List.of("w:1:1"), cs.drain().upserts());
    }

    @Test
    void drainClearsState() {
        ClaimChangeSet cs = new ClaimChangeSet();
        cs.recordUpsert("w:1:1");
        cs.drain();
        assertTrue(cs.isEmpty());
        assertTrue(cs.drain().upserts().isEmpty());
    }

    @Test
    void recordingAfterDrainAccumulatesForNextFlush() {
        ClaimChangeSet cs = new ClaimChangeSet();
        cs.recordUpsert("w:1:1");
        cs.drain();
        cs.recordDelete("w:1:1");
        assertEquals(List.of("w:1:1"), cs.drain().deletes());
    }

    @Test
    void drainSnapshotIsImmutable() {
        ClaimChangeSet cs = new ClaimChangeSet();
        cs.recordUpsert("w:1:1");
        ClaimChangeSet.Drain drain = cs.drain();
        assertThrows(UnsupportedOperationException.class, () -> drain.upserts().add("x"));
    }
}
