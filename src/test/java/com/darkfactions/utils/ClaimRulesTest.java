package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ClaimRulesTest {

    private static final String WORLD = "world";
    private static final UUID OURS = new UUID(0, 1);
    private static final UUID THEIRS = new UUID(0, 2);

    private Map<String, UUID> claims() {
        return new HashMap<>();
    }

    @Test
    void keyFormatMatchesManagerConvention() {
        assertEquals("world:3:-4", ClaimRules.key(WORLD, 3, -4));
    }

    @Test
    void isOwnedByDistinguishesOwnerMissingAndOther() {
        Map<String, UUID> claims = claims();
        claims.put(ClaimRules.key(WORLD, 0, 0), OURS);
        assertTrue(ClaimRules.isOwnedBy(claims, WORLD, 0, 0, OURS));
        assertFalse(ClaimRules.isOwnedBy(claims, WORLD, 0, 0, THEIRS));
        assertFalse(ClaimRules.isOwnedBy(claims, WORLD, 9, 9, OURS), "unclaimed chunk is owned by nobody");
    }

    @Test
    void adjacencyIsOrthogonalOnly() {
        Map<String, UUID> claims = claims();
        claims.put(ClaimRules.key(WORLD, 1, 0), OURS); // east neighbour
        assertTrue(ClaimRules.isAdjacentToClaim(claims, WORLD, 0, 0, OURS));

        Map<String, UUID> diagonal = claims();
        diagonal.put(ClaimRules.key(WORLD, 1, 1), OURS); // diagonal only
        assertFalse(ClaimRules.isAdjacentToClaim(diagonal, WORLD, 0, 0, OURS),
                "diagonal neighbours must not count as connected");
    }

    @Test
    void adjacencyIgnoresOtherFactionsClaims() {
        Map<String, UUID> claims = claims();
        claims.put(ClaimRules.key(WORLD, 1, 0), THEIRS);
        assertFalse(ClaimRules.isAdjacentToClaim(claims, WORLD, 0, 0, OURS),
                "an enemy's adjacent claim does not satisfy our connection rule");
    }

    @Test
    void bufferDisabledWhenZeroOrNegative() {
        Map<String, UUID> claims = claims();
        claims.put(ClaimRules.key(WORLD, 1, 0), THEIRS);
        assertFalse(ClaimRules.violatesBuffer(claims, WORLD, 0, 0, OURS, 0));
        assertFalse(ClaimRules.violatesBuffer(claims, WORLD, 0, 0, OURS, -1));
    }

    @Test
    void bufferTrippedByOtherFactionWithinRadius() {
        Map<String, UUID> claims = claims();
        claims.put(ClaimRules.key(WORLD, 1, 0), THEIRS);
        assertTrue(ClaimRules.violatesBuffer(claims, WORLD, 0, 0, OURS, 1));
    }

    @Test
    void bufferIgnoresOwnClaimsAndCentre() {
        Map<String, UUID> claims = claims();
        claims.put(ClaimRules.key(WORLD, 1, 0), OURS);   // our own neighbour - fine
        claims.put(ClaimRules.key(WORLD, 0, 0), THEIRS); // centre is excluded from the scan
        assertFalse(ClaimRules.violatesBuffer(claims, WORLD, 0, 0, OURS, 1));
    }

    @Test
    void bufferDoesNotReachBeyondRadius() {
        Map<String, UUID> claims = claims();
        claims.put(ClaimRules.key(WORLD, 2, 0), THEIRS); // two chunks away
        assertFalse(ClaimRules.violatesBuffer(claims, WORLD, 0, 0, OURS, 1));
        assertTrue(ClaimRules.violatesBuffer(claims, WORLD, 0, 0, OURS, 2));
    }
}
