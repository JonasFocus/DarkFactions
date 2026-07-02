package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.darkfactions.utils.ProtectionRules.Action;

class ProtectionRulesTest {

    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID ACTOR = new UUID(0, 2);

    @Test
    void disabledProtectionAllowsEverything() {
        assertFalse(ProtectionRules.shouldDeny(
                false, OWNER, ACTOR, false, false, true, true, true,
                false, false, false, Action.BREAK));
    }

    @Test
    void wildernessAllowsEverything() {
        assertFalse(ProtectionRules.shouldDeny(
                true, null, ACTOR, false, false, false, false, true,
                false, false, false, Action.BREAK));
    }

    @Test
    void factionlessPlayersAreDenied() {
        assertTrue(ProtectionRules.shouldDeny(
                true, OWNER, null, false, false, false, false, true,
                true, true, true, Action.BREAK));
    }

    @Test
    void sameFactionIsAlwaysAllowed() {
        assertFalse(ProtectionRules.shouldDeny(
                true, OWNER, OWNER, true, false, false, false, true,
                false, false, false, Action.BREAK));
    }

    @Test
    void alliesRespectPerActionFlags() {
        assertTrue(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, true, false, false, true,
                false, false, false, Action.BREAK));
        assertFalse(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, true, false, false, true,
                true, false, false, Action.BREAK));
        assertTrue(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, true, false, false, true,
                true, false, false, Action.PLACE));
        assertFalse(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, true, false, false, true,
                true, true, false, Action.PLACE));
        assertTrue(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, true, false, false, true,
                true, true, false, Action.INTERACT));
        assertFalse(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, true, false, false, true,
                true, true, true, Action.INTERACT));
    }

    @Test
    void raidableBypassAllowsEnemyBreakAndPlaceOnly() {
        assertFalse(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, false, true, true, true,
                false, false, false, Action.BREAK));
        assertFalse(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, false, true, true, true,
                false, false, false, Action.PLACE));
        assertTrue(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, false, true, true, true,
                false, false, false, Action.INTERACT));
    }

    @Test
    void raidableBypassRequiresEnemyRelationAndConfigFlag() {
        assertTrue(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, false, true, true, false,
                false, false, false, Action.BREAK));
        assertTrue(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, false, true, false, true,
                false, false, false, Action.BREAK));
        assertTrue(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, false, false, true, true,
                false, false, false, Action.BREAK));
    }

    @Test
    void nonAllyNonRaidableEnemiesAreDenied() {
        assertTrue(ProtectionRules.shouldDeny(
                true, OWNER, ACTOR, false, false, true, false, true,
                true, true, true, Action.BREAK));
    }
}
