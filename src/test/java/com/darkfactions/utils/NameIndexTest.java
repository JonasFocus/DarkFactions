package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class NameIndexTest {

    @Test
    void forwardAndReverseLookupWork() {
        NameIndex index = new NameIndex();
        UUID id = UUID.randomUUID();
        index.put(id, "Steve");

        assertEquals("Steve", index.nameOf(id));
        assertEquals(id, index.uuidOf("Steve"));
    }

    @Test
    void reverseLookupIsCaseInsensitive() {
        NameIndex index = new NameIndex();
        UUID id = UUID.randomUUID();
        index.put(id, "Steve");

        assertEquals(id, index.uuidOf("steve"));
        assertEquals(id, index.uuidOf("STEVE"));
    }

    @Test
    void renamingDropsTheOldReverseEntry() {
        NameIndex index = new NameIndex();
        UUID id = UUID.randomUUID();
        index.put(id, "OldName");
        index.put(id, "NewName");

        assertNull(index.uuidOf("OldName"));
        assertEquals(id, index.uuidOf("NewName"));
        assertEquals("NewName", index.nameOf(id));
        assertEquals(1, index.size());
    }

    @Test
    void aFreedNameTakenByAnotherUuidIsNotClobbered() {
        NameIndex index = new NameIndex();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        index.put(first, "Name");
        index.put(second, "Name");   // same name, different account
        index.put(first, "Renamed"); // first changes name; must not wipe second's entry

        assertEquals(second, index.uuidOf("Name"));
        assertEquals(first, index.uuidOf("Renamed"));
    }

    @Test
    void nullArgumentsAreIgnored() {
        NameIndex index = new NameIndex();
        index.put(null, "x");
        index.put(UUID.randomUUID(), null);

        assertEquals(0, index.size());
        assertNull(index.uuidOf(null));
        assertNull(index.nameOf(UUID.randomUUID()));
    }

    @Test
    void containsAndSizeReflectContents() {
        NameIndex index = new NameIndex();
        UUID id = UUID.randomUUID();
        assertFalse(index.contains(id));
        index.put(id, "A");
        assertTrue(index.contains(id));
        assertEquals(1, index.size());
    }

    @Test
    void entriesReturnsDefensiveCopy() {
        NameIndex index = new NameIndex();
        UUID id = UUID.randomUUID();
        index.put(id, "A");

        index.entries().clear();

        assertEquals(1, index.size());
        assertEquals("A", index.nameOf(id));
    }
}
