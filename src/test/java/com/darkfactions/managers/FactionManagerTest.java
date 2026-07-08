package com.darkfactions.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class FactionManagerTest {

    @Test
    void allyRequestIsPendingUntilAccepted() {
        FactionManager manager = new FactionManager(null);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(manager.sendAllyRequest(a, b), "first request should be recorded");
        assertTrue(manager.hasPendingAllyRequest(a, b), "b should see a pending request from a");
        assertFalse(manager.hasPendingAllyRequest(b, a), "a has not been asked by b yet");
        assertFalse(manager.acceptAllyRequest(b, a), "accept without loaded factions cannot form the ally");
    }

    @Test
    void duplicateRequestFromSameFactionStaysPending() {
        FactionManager manager = new FactionManager(null);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(manager.sendAllyRequest(a, b));
        assertFalse(manager.sendAllyRequest(a, b), "re-requesting the same pair is rejected");
        assertTrue(manager.hasPendingAllyRequest(a, b));
    }

    @Test
    void denyClearsPendingRequest() {
        FactionManager manager = new FactionManager(null);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(manager.sendAllyRequest(a, b));
        assertTrue(manager.denyAllyRequest(b, a));
        assertFalse(manager.hasPendingAllyRequest(a, b));
        assertFalse(manager.denyAllyRequest(b, a), "second deny finds nothing");
    }

    @Test
    void clearAllianceRequestsDropsBothDirections() {
        FactionManager manager = new FactionManager(null);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        manager.sendAllyRequest(a, b);
        manager.clearAllianceRequests(a, b);
        assertFalse(manager.hasPendingAllyRequest(a, b), "cleared request must be gone");

        assertTrue(manager.sendAllyRequest(b, a), "a fresh request from b to a is recorded on its own");
        assertTrue(manager.hasPendingAllyRequest(b, a));
    }
}
