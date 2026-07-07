package com.darkfactions.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class FactionManagerTest {

    @Test
    void allianceRequiresMutualConsent() {
        FactionManager manager = new FactionManager(null);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertFalse(manager.requestAlliance(a, b), "first request is only pending, not yet mutual");
        assertTrue(manager.hasPendingAllyRequest(b, a), "b should see a pending request from a");
        assertFalse(manager.hasPendingAllyRequest(a, b), "a has not been asked by b yet");

        assertTrue(manager.requestAlliance(b, a), "b reciprocating completes mutual consent");
        assertFalse(manager.hasPendingAllyRequest(b, a), "consumed request should be cleared");
    }

    @Test
    void duplicateRequestFromSameFactionStaysPending() {
        FactionManager manager = new FactionManager(null);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertFalse(manager.requestAlliance(a, b));
        assertFalse(manager.requestAlliance(a, b), "re-requesting is not self-confirmation");
        assertTrue(manager.hasPendingAllyRequest(b, a));
    }

    @Test
    void clearAllianceRequestsDropsBothDirections() {
        FactionManager manager = new FactionManager(null);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        manager.requestAlliance(a, b);
        manager.clearAllianceRequests(a, b);
        assertFalse(manager.hasPendingAllyRequest(b, a), "cleared request must be gone");

        assertFalse(manager.requestAlliance(b, a), "no leftover request should auto-complete the alliance");
        assertTrue(manager.hasPendingAllyRequest(a, b), "the new request from b to a is recorded on its own");
    }
}
