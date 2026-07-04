package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.darkfactions.utils.PvpRules.Territory;
import com.darkfactions.utils.PvpRules.Verdict;

class PvpRulesTest {

    @Test
    void sameFactionDeniedWhenToggleOffAndFactionPvpDisabled() {
        assertEquals(Verdict.DENY_FACTION_PVP_DISABLED, PvpRules.resolve(
                true, true, false, false, true, false, Territory.OWN, true, true, true, true));
    }

    @Test
    void sameFactionIgnoresToggleWhenNotRespected() {
        assertEquals(Verdict.ALLOW_NO_TAG, PvpRules.resolve(
                true, false, false, false, true, false, Territory.OWN, true, true, true, true));
    }

    @Test
    void allyIsAlwaysDenied() {
        assertEquals(Verdict.DENY_ALLY, PvpRules.resolve(
                false, true, true, true, true, false, Territory.WILDERNESS, true, true, true, true));
    }

    @Test
    void allyDeniedEvenWhenAllTerritoryTogglesAllow() {
        assertEquals(Verdict.DENY_ALLY, PvpRules.resolve(
                false, true, true, true, true, false, Territory.OTHER, true, true, true, true));
    }

    @Test
    void wildernessAllowedWhenToggleOn() {
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                false, true, true, false, true, false, Territory.WILDERNESS, true, true, true, true));
    }

    @Test
    void wildernessDeniedWhenToggleOff() {
        assertEquals(Verdict.DENY_TERRITORY, PvpRules.resolve(
                false, true, true, false, true, false, Territory.WILDERNESS, false, true, true, true));
    }

    @Test
    void sameFactionInWildernessStillCombatTags() {
        // The no-tag carve-out only applies to the victim's own territory, not wilderness.
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                true, true, true, false, true, false, Territory.WILDERNESS, true, true, true, true));
    }

    @Test
    void ownTerritoryDeniedWhenToggleOff() {
        assertEquals(Verdict.DENY_TERRITORY, PvpRules.resolve(
                false, true, true, false, true, false, Territory.OWN, true, false, true, true));
    }

    @Test
    void sameFactionInOwnTerritoryAllowedWithoutTag() {
        // Friendly sparring in your own base: allowed, but no combat tag/flight-lock.
        assertEquals(Verdict.ALLOW_NO_TAG, PvpRules.resolve(
                true, true, true, false, true, false, Territory.OWN, true, true, true, true));
    }

    @Test
    void neutralFactionAttackerInOwnTerritoryAllowedWithoutTag() {
        // Attacker belongs to a faction that is neither an ally nor an enemy.
        assertEquals(Verdict.ALLOW_NO_TAG, PvpRules.resolve(
                false, true, true, false, true, false, Territory.OWN, true, true, true, true));
    }

    @Test
    void enemyFactionAttackerInOwnTerritoryCombatTags() {
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                false, true, true, false, true, true, Territory.OWN, true, true, true, true));
    }

    @Test
    void factionlessAttackerInVictimsOwnTerritoryCombatTags() {
        // No carve-out applies when the attacker has no faction at all.
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                false, true, true, false, false, false, Territory.OWN, true, true, true, true));
    }

    @Test
    void allyTerritoryAllowedWhenToggleOn() {
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                false, true, true, false, true, false, Territory.ALLY, true, true, true, true));
    }

    @Test
    void allyTerritoryDeniedWhenToggleOff() {
        assertEquals(Verdict.DENY_TERRITORY, PvpRules.resolve(
                false, true, true, false, true, false, Territory.ALLY, true, true, false, true));
    }

    @Test
    void enemyOrUnaffiliatedTerritoryAllowedWhenToggleOn() {
        assertEquals(Verdict.ALLOW, PvpRules.resolve(
                false, true, true, false, true, false, Territory.OTHER, true, true, true, true));
    }

    @Test
    void enemyOrUnaffiliatedTerritoryDeniedWhenToggleOff() {
        assertEquals(Verdict.DENY_TERRITORY, PvpRules.resolve(
                false, true, true, false, true, false, Territory.OTHER, true, true, true, false));
    }

    @Test
    void factionlessAttackerInEnemyTerritoryFollowsEnemyToggle() {
        assertEquals(Verdict.DENY_TERRITORY, PvpRules.resolve(
                false, true, false, false, false, false, Territory.OTHER, true, true, true, false));
    }
}
