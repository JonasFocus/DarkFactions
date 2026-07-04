package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.darkfactions.utils.PvpRules.Territory;
import com.darkfactions.utils.PvpRules.Verdict;

class PvpRulesTest {

    @Test
    void sameFactionDeniedWhenToggleOffAndFactionPvpDisabled() {
        assertEquals(Verdict.DENY_FACTION_PVP_DISABLED, PvpRules.resolve(
                true, true, false, false, Territory.OWN, true, true, true, true));
    }

    @Test
    void sameFactionAllowedWhenFactionPvpEnabled() {
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                true, true, true, false, Territory.OWN, true, true, true, true));
    }

    @Test
    void sameFactionIgnoresToggleWhenNotRespected() {
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                true, false, false, false, Territory.OWN, true, true, true, true));
    }

    @Test
    void allyIsAlwaysDenied() {
        assertEquals(Verdict.DENY_ALLY, PvpRules.resolve(
                false, true, true, true, Territory.WILDERNESS, true, true, true, true));
    }

    @Test
    void allyDeniedEvenWhenAllTerritoryTogglesAllow() {
        assertEquals(Verdict.DENY_ALLY, PvpRules.resolve(
                false, true, true, true, Territory.OTHER, true, true, true, true));
    }

    @Test
    void wildernessAllowedWhenToggleOn() {
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                false, true, true, false, Territory.WILDERNESS, true, true, true, true));
    }

    @Test
    void wildernessDeniedWhenToggleOff() {
        assertEquals(Verdict.DENY_TERRITORY, PvpRules.resolve(
                false, true, true, false, Territory.WILDERNESS, false, true, true, true));
    }

    @Test
    void ownTerritoryAllowedWhenToggleOn() {
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                false, true, true, false, Territory.OWN, true, true, true, true));
    }

    @Test
    void ownTerritoryDeniedWhenToggleOff() {
        assertEquals(Verdict.DENY_TERRITORY, PvpRules.resolve(
                false, true, true, false, Territory.OWN, true, false, true, true));
    }

    @Test
    void allyTerritoryAllowedWhenToggleOn() {
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                false, true, true, false, Territory.ALLY, true, true, true, true));
    }

    @Test
    void allyTerritoryDeniedWhenToggleOff() {
        assertEquals(Verdict.DENY_TERRITORY, PvpRules.resolve(
                false, true, true, false, Territory.ALLY, true, true, false, true));
    }

    @Test
    void enemyOrUnaffiliatedTerritoryAllowedWhenToggleOn() {
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                false, true, true, false, Territory.OTHER, true, true, true, true));
    }

    @Test
    void enemyOrUnaffiliatedTerritoryDeniedWhenToggleOff() {
        assertEquals(Verdict.DENY_TERRITORY, PvpRules.resolve(
                false, true, true, false, Territory.OTHER, true, true, true, false));
    }

    @Test
    void factionlessAttackerInEnemyTerritoryFollowsEnemyToggle() {
        // victimFaction == null on both flags, wilderness/ally n/a -> territory is OTHER
        assertEquals(Verdict.DENY_TERRITORY, PvpRules.resolve(
                false, true, false, false, Territory.OTHER, true, true, true, false));
    }
}
